package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntityBogged;
import cn.nukkit.entity.mob.EntityDrowned;
import cn.nukkit.entity.mob.EntityHusk;
import cn.nukkit.entity.mob.EntityParched;
import cn.nukkit.entity.mob.EntityPhantom;
import cn.nukkit.entity.mob.EntitySkeleton;
import cn.nukkit.entity.mob.EntityStray;
import cn.nukkit.entity.mob.EntityWither;
import cn.nukkit.entity.mob.EntityWitherSkeleton;
import cn.nukkit.entity.mob.EntityZombie;
import cn.nukkit.entity.mob.EntityZombiePigman;
import cn.nukkit.entity.mob.EntityZombieVillager;
import cn.nukkit.entity.mob.EntityZombieVillagerV1;
import cn.nukkit.entity.mob.EntityZoglin;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

public class EntityArmadillo extends EntityWalkingAnimal {

    public static final int NETWORK_ID = 142;
    private static final int THREAT_RANGE_SQUARED = 49;
    private static final int UNROLL_DELAY_TICKS = 80;

    private boolean rolledUp;
    private int threatSearchCooldown;
    private int ticksWithoutThreat;
    private int lastAttackerTicks;
    private Entity lastAttacker;

    public EntityArmadillo(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(12);
        super.initEntity();
    }

    @Override
    public float getWidth() {
        if (this.isBaby()) {
            return 0.42f;
        }
        return 0.7f;
    }

    @Override
    public float getHeight() {
        if (this.isBaby()) {
            return 0.39f;
        }
        return 0.65f;
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : Utils.rand(1, 3);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    public boolean isRolledUp() {
        return this.rolledUp;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (!this.getServer().getMobAiEnabled()) {
            this.setRolledUp(false);
            this.lastAttacker = null;
            this.lastAttackerTicks = 0;
            return super.onUpdate(currentTick);
        }
        if (this.closed || !this.isAlive()) {
            return super.onUpdate(currentTick);
        }

        if (this.lastAttackerTicks > 0 && --this.lastAttackerTicks == 0) {
            this.lastAttacker = null;
        }
        if (this.threatSearchCooldown <= 0) {
            this.threatSearchCooldown = 5;
            if (this.hasNearbyThreat()) {
                this.ticksWithoutThreat = 0;
                if (this.canRollUp()) {
                    this.setRolledUp(true);
                }
            } else if (this.rolledUp) {
                this.ticksWithoutThreat += 5;
                if (this.ticksWithoutThreat >= UNROLL_DELAY_TICKS) {
                    this.setRolledUp(false);
                }
            }
        }
        this.threatSearchCooldown--;

        if (this.rolledUp && (this.isInsideOfWater() || this.isOnFire() || !this.onGround
                || this.isLeashed() || this.getRiding() != null || this.getPanicTicks() > 0)) {
            this.setRolledUp(false);
        }
        return super.onUpdate(currentTick);
    }

    @Override
    public Vector3 updateMove(int tickDiff) {
        if (this.rolledUp) {
            this.motionX = this.motionY = this.motionZ = 0;
            return null;
        }
        return super.updateMove(tickDiff);
    }

    @Override
    public boolean canTarget(Entity entity) {
        return !this.rolledUp && super.canTarget(entity);
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        if (this.rolledUp) {
            // The shell applies damage * 0.5 - 1.0, including environmental damage.
            ev.setDamage(Math.max(0, ev.getDamage() * 0.5f - 1.0f));
        }
        boolean attacked = super.attack(ev);
        if (attacked && !ev.isCancelled() && ev.getFinalDamage() > 0
                && ev instanceof EntityDamageByEntityEvent) {
            Entity attacker = ((EntityDamageByEntityEvent) ev).getDamager();
            if (attacker != this) {
                this.lastAttacker = attacker;
                this.lastAttackerTicks = 400;
                this.doPanic(false);
                if (this.canRollUp()) {
                    this.setRolledUp(true);
                    this.ticksWithoutThreat = 0;
                }
            }
        }
        return attacked;
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (this.rolledUp && item.getId() == Item.SPIDER_EYE) {
            return false;
        }
        return super.onInteract(player, item, clickedPos);
    }

    private boolean canRollUp() {
        return !this.isInsideOfWater() && !this.isOnFire() && this.onGround && !this.isLeashed()
                && this.getRiding() == null && this.getPanicTicks() <= 0;
    }

    private void setRolledUp(boolean rolledUp) {
        if (this.rolledUp == rolledUp) {
            return;
        }
        this.rolledUp = rolledUp;
        this.ticksWithoutThreat = 0;
        this.setDataFlag(DATA_FLAGS_EXTENDED, DATA_FLAG_ROLLING, rolledUp);
        if (rolledUp) {
            this.target = null;
            this.followTarget = null;
            this.stayTime = 0;
        }
    }

    private boolean hasNearbyThreat() {
        if (this.lastAttacker != null && !this.lastAttacker.closed && this.lastAttacker.isAlive()
                && this.distanceSquared(this.lastAttacker) <= THREAT_RANGE_SQUARED) {
            return true;
        }
        for (Entity entity : this.level.getNearbyEntities(this.getBoundingBox().grow(7, 7, 7), this)) {
            if (entity instanceof Player) {
                Player player = (Player) entity;
                if ((player.isSurvival() || player.isAdventure()) && (player.isSprinting() || player.getRiding() != null)
                        && this.distanceSquared(player) <= THREAT_RANGE_SQUARED) {
                    return true;
                }
            } else if (entity.isAlive() && !entity.closed && this.distanceSquared(entity) <= THREAT_RANGE_SQUARED
                    && isUndeadThreat(entity)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUndeadThreat(Entity entity) {
        return entity instanceof EntityZombie || entity instanceof EntityHusk || entity instanceof EntityDrowned
                || entity instanceof EntityZombiePigman || entity instanceof EntityZombieVillager
                || entity instanceof EntityZombieVillagerV1 || entity instanceof EntityZombieNautilus
                || entity instanceof EntitySkeleton || entity instanceof EntityStray || entity instanceof EntityBogged
                || entity instanceof EntityParched || entity instanceof EntityWitherSkeleton
                || entity instanceof EntityPhantom || entity instanceof EntityWither || entity instanceof EntityZoglin;
    }
}


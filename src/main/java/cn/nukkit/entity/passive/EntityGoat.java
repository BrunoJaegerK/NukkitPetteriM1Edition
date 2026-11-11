package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

import java.util.HashMap;

public class EntityGoat extends EntityWalkingAnimal {

    public static final int NETWORK_ID = 128;
    private static final int RAM_PREPARATION_TICKS = 20;
    private static final int RAM_CHARGE_TICKS = 40;

    private EntityCreature ramTarget;
    private int ramCooldownTicks;
    private int ramPreparationTicks;
    private int ramChargeTicks;
    private RamState ramState = RamState.IDLE;

    private enum RamState {
        IDLE,
        PREPARING,
        CHARGING
    }

    public EntityGoat(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(10);
        super.initEntity();
        this.ramCooldownTicks = Utils.rand(600, 6000);
    }

    @Override
    public float getWidth() {
        if (this.isBaby()) {
            return 0.65f;
        }
        return 1.3f;
    }

    @Override
    public float getHeight() {
        if (this.isBaby()) {
            return 0.45f;
        }
        return 0.9f;
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : Utils.rand(1, 3);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public double getSpeed() {
        return 1.1;
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        this.ramCooldownTicks = Math.max(0, this.ramCooldownTicks - tickDiff);
        return super.entityBaseTick(tickDiff);
    }

    @Override
    public boolean canTarget(Entity entity) {
        if (this.isInLove() && entity instanceof EntityGoat) {
            return super.canTarget(entity);
        }
        return this.getServer().getMobAiEnabled() && this.getPanicTicks() <= 0
                && entity instanceof EntityCreature && this.isRamTarget((EntityCreature) entity);
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (this.isInLove() && creature instanceof EntityGoat) {
            return super.targetOption(creature, distance);
        }
        if (this.ramState == RamState.IDLE && creature instanceof Player && distance <= 64
                && ((Player) creature).getInventory().getItemInHandFast().getId() == Item.WHEAT) {
            return creature.isAlive() && !creature.closed;
        }
        if (this.ramState == RamState.PREPARING) {
            return false;
        }
        if (this.ramState == RamState.CHARGING) {
            return creature == this.ramTarget;
        }
        return this.getServer().getMobAiEnabled() && this.ramCooldownTicks <= 0
                && distance >= 16 && distance <= 256 && this.isRamTarget(creature);
    }

    @Override
    public Vector3 updateMove(int tickDiff) {
        if (!this.getServer().getMobAiEnabled()) {
            this.resetRam(false);
            return super.updateMove(tickDiff);
        }

        if (this.ramState == RamState.IDLE && this.ramCooldownTicks <= 0
                && this.followTarget instanceof EntityCreature) {
            EntityCreature candidate = (EntityCreature) this.followTarget;
            double distance = this.distanceSquared(candidate);
            if (distance >= 16 && distance <= 256 && this.isRamTarget(candidate)
                    && !this.isTemptingPlayer(candidate) && this.hasLineOfSight(candidate)) {
                this.ramTarget = candidate;
                this.ramState = RamState.PREPARING;
                this.ramPreparationTicks = RAM_PREPARATION_TICKS;
                this.setDataFlag(DATA_FLAGS, DATA_FLAG_RAM_ATTACK, true);
                this.level.addSound(this, Sound.MOB_GOAT_PREPARE_RAM);
            }
        }

        if (this.ramState == RamState.PREPARING) {
            if (!this.isRamTarget(this.ramTarget) || this.distanceSquared(this.ramTarget) > 400) {
                this.resetRam(true);
                return super.updateMove(tickDiff);
            }
            this.followTarget = null;
            this.target = this.clone();
            this.motionX = 0;
            this.motionZ = 0;
            this.ramPreparationTicks -= tickDiff;
            if (this.ramPreparationTicks <= 0) {
                this.ramState = RamState.CHARGING;
                this.ramChargeTicks = RAM_CHARGE_TICKS;
            }
            return super.updateMove(tickDiff);
        }

        if (this.ramState == RamState.CHARGING) {
            if (!this.isRamTarget(this.ramTarget)) {
                this.resetRam(true);
                return super.updateMove(tickDiff);
            }
            if (this.distanceSquared(this.ramTarget) <= 2.25) {
                float baseDamage = this.isBaby() ? 1 : 2;
                HashMap<EntityDamageEvent.DamageModifier, Float> damage = new HashMap<>();
                damage.put(EntityDamageEvent.DamageModifier.BASE, baseDamage);
                if (this.ramTarget instanceof Player) {
                    float armorPoints = 0;
                    for (Item armor : ((Player) this.ramTarget).getInventory().getArmorContents()) {
                        armorPoints += this.getArmorPoints(armor.getId());
                    }
                    damage.put(EntityDamageEvent.DamageModifier.ARMOR,
                            (float) -Math.floor(baseDamage * armorPoints * 0.04));
                }
                this.ramTarget.attack(new EntityDamageByEntityEvent(this, this.ramTarget,
                        EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage, 2.5f));
                this.level.addSound(this, Sound.MOB_GOAT_RAM_IMPACT);
                this.resetRam(true);
                return super.updateMove(tickDiff);
            }
            this.followTarget = null;
            this.target = this.ramTarget;
            this.moveMultiplier = 1.8f;
            this.ramChargeTicks -= tickDiff;
            if (this.ramChargeTicks <= 0) {
                this.resetRam(true);
            }
            return super.updateMove(tickDiff);
        }

        return super.updateMove(tickDiff);
    }

    private boolean isRamTarget(EntityCreature entity) {
        if (entity == null || entity == this || entity instanceof EntityGoat || entity.closed || !entity.isAlive()
                || !entity.canBeFollowed()) {
            return false;
        }
        if (entity instanceof Player) {
            Player player = (Player) entity;
            return player.isSurvival() || player.isAdventure();
        }
        return true;
    }

    private boolean isTemptingPlayer(Entity entity) {
        return entity instanceof Player && ((Player) entity).getInventory().getItemInHandFast().getId() == Item.WHEAT;
    }

    private void resetRam(boolean startCooldown) {
        this.ramState = RamState.IDLE;
        this.ramTarget = null;
        this.ramPreparationTicks = 0;
        this.ramChargeTicks = 0;
        this.moveMultiplier = 1.0f;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_RAM_ATTACK, false);
        if (startCooldown) {
            this.ramCooldownTicks = Utils.rand(600, 6000);
        }
    }
}


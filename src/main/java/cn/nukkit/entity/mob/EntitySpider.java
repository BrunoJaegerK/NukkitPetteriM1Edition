package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.block.*;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityArthropod;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.passive.EntityArmadillo;
import cn.nukkit.entity.passive.EntityIronGolem;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EntitySpider extends EntityWalkingMob implements EntityArthropod {

    public static final int NETWORK_ID = 35;

    private int angry = 0;
    private int brightnessCheckCooldown;
    private int leapCooldown;
    private boolean dark = true;
    private EntityCreature angryTarget;

    public EntitySpider(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 1.4f;
    }

    @Override
    public float getHeight() {
        return 0.9f;
    }

    @Override
    public double getSpeed() {
        return 1.13;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(16);
        super.initEntity();
        this.setDamage(new int[]{0, 2, 2, 3});
        this.setCanClimb(true);
        this.setCanClimbWalls(true);
        this.setDataFlag(DATA_FLAGS_EXTENDED, DATA_FLAG_RENDER_WHEN_INVISIBLE, true);
    }

    @Override
    protected boolean checkJump(double dx, double dz) {
        if (this.motionY == this.getGravity() * 2 && this.canSwimIn(level.getBlockIdAt(chunk, this.getFloorX(), this.getFloorY(), this.getFloorZ()))) {
            return true;
        } else {
            if (this.canSwimIn(level.getBlockIdAt(chunk, NukkitMath.floorDouble(this.x), (int) (this.y + 0.8), NukkitMath.floorDouble(this.z)))) {
                this.motionY = this.getGravity() * 2;
                return true;
            }
        }

        if (this.followTarget == null && (!this.onGround || this.stayTime > 0)) {
            return false;
        }

        Block block = this.getLevel().getBlock(chunk, NukkitMath.floorDouble(this.x + dx), this.getFloorY(), NukkitMath.floorDouble(this.z + dz), false);
        BlockFace direction = this.getDirection();
        if (direction == null) {
            return false;
        }
        Block directionBlock = block.getSide(direction);
        if (!directionBlock.canPassThrough()) {
            this.motionY = this.getGravity() * 3;
            return true;
        }
        return false;
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.isAngry() && player instanceof EntityCreature && this.angryTarget != player) {
            this.angryTarget = (EntityCreature) player;
            this.setAngry(Utils.rand(140, 260));
        }
        double distance = this.distanceSquared(player);
        if (this.isAngry() && this.onGround && this.leapCooldown <= 0
                && distance > 4 && distance <= 64 && Utils.rand(1, 10) == 1) {
            this.motionY = 0.4;
            this.leapCooldown = 40;
        }
        if (!this.isFriendly() || !(player instanceof Player)) {
            if (this.isAngry()) {
                if (this.attackDelay > 23 && distance < 2 + this.getWidth() * this.getWidth()) {
                    this.attackDelay = 0;
                    HashMap<EntityDamageEvent.DamageModifier, Float> damage = new HashMap<>();
                    damage.put(EntityDamageEvent.DamageModifier.BASE, (float) this.getDamage());
                    if (player instanceof Player) {
                        float points = 0;
                        for (Item i : ((Player) player).getInventory().getArmorContents()) {
                            points += this.getArmorPoints(i.getId());
                        }
                        damage.put(EntityDamageEvent.DamageModifier.ARMOR,
                                (float) (damage.getOrDefault(EntityDamageEvent.DamageModifier.ARMOR, 0f) - Math.floor(damage.getOrDefault(EntityDamageEvent.DamageModifier.BASE, 1f) * points * 0.04)));
                    }
                    player.attack(new EntityDamageByEntityEvent(this, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage));
                }
            }
        }
    }

    @Override
    public Vector3 updateMove(int tickDiff) {
        EntityArmadillo armadillo = this.findNearbyUnrolledArmadillo();
        if (armadillo != null) {
            double dx = this.x - armadillo.x;
            double dz = this.z - armadillo.z;
            if (dx * dx + dz * dz < 0.01) {
                dx = 1;
                dz = 0;
            }
            double length = Math.sqrt(dx * dx + dz * dz);
            this.followTarget = null;
            this.target = new Vector3(this.x + dx / length * 8, this.y, this.z + dz / length * 8);
            this.stayTime = 0;
            this.moveTime = 20;
            this.moveMultiplier = 1.25f;
        } else {
            this.moveMultiplier = 1.0f;
        }
        return super.updateMove(tickDiff);
    }

    private EntityArmadillo findNearbyUnrolledArmadillo() {
        if (!this.getServer().getMobAiEnabled()) {
            return null;
        }
        EntityArmadillo nearest = null;
        double nearestDistance = 36;
        for (Entity entity : this.level.getNearbyEntities(this.getBoundingBox().grow(6, 4, 6), this)) {
            if (entity instanceof EntityArmadillo && !((EntityArmadillo) entity).isRolledUp()) {
                double distance = this.distanceSquared(entity);
                if (distance < nearestDistance) {
                    nearest = (EntityArmadillo) entity;
                    nearestDistance = distance;
                }
            }
        }
        return nearest;
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        boolean attacked = super.attack(ev);

        if (attacked && !ev.isCancelled() && ev instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) ev).getDamager();
            if (damager instanceof EntityCreature && damager != this) {
                this.setAngry(Utils.rand(140, 260));
                this.angryTarget = (EntityCreature) damager;
                this.setTarget(damager);
            }
        }

        return attacked;
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        for (int i = 0; i < Utils.rand(0, 2); i++) {
            drops.add(Item.get(Item.STRING, 0, 1));
        }

        for (int i = 0; i < (Utils.rand(0, 2) == 0 ? 1 : 0); i++) {
            drops.add(Item.get(Item.SPIDER_EYE, 0, 1));
        }

        return drops.toArray(new Item[0]);
    }

    @Override
    public int getKillExperience() {
        return 5;
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (getServer().getDifficulty() == 0) {
            this.close();
            return true;
        }

        if (this.brightnessCheckCooldown <= 0) {
            int brightness = this.level.getFullLight(this);
            this.dark = brightness < 8;
            this.brightnessCheckCooldown = 10;
        }
        this.brightnessCheckCooldown -= tickDiff;
        this.leapCooldown = Math.max(0, this.leapCooldown - tickDiff);
        if (this.angry > 0) {
            this.angry = Math.max(0, this.angry - tickDiff);
        }

        return super.entityBaseTick(tickDiff);
    }

    public boolean isAngry() {
        return this.angry > 0 || this.dark;
    }

    public void setAngry(int val) {
        this.angry = val;
    }

    @Override
    public boolean canTarget(Entity entity) {
        return entity instanceof Player || entity instanceof EntityIronGolem || entity instanceof EntitySnowGolem
                ? entity.canBeFollowed() : false;
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (this.findNearbyUnrolledArmadillo() != null || !this.isAngry() || !creature.isAlive() || creature.closed || distance > 256) {
            return false;
        }
        if (creature instanceof Player) {
            return super.targetOption(creature, distance);
        }
        return creature instanceof EntityIronGolem || creature instanceof EntitySnowGolem;
    }
}


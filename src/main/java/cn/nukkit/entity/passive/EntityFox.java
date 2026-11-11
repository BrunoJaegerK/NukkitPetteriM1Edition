package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Utils;

public class EntityFox extends EntityWalkingAnimal {

    public static final int NETWORK_ID = 121;
    private int preyAttackCooldown;
    private long lastThreatSearchTick = -1;
    private Entity nearbyThreat;

    public EntityFox(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.7f;
    }

    @Override
    public float getHeight() {
        return 0.6f;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(20);
        super.initEntity();
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : Utils.rand(1, 2);
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (item.getId() == Item.SWEET_BERRIES && !this.isBaby()) {
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_EAT);
            this.level.addParticle(new ItemBreakParticle(this.add(0, this.getMountedYOffset(), 0), item));
            this.setInLove();
            return true;
        }
        return super.onInteract(player, item, clickedPos);
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (this.findNearbyThreat() != null) {
            return false;
        }
        if (this.isPrey(creature)) {
            return this.getServer().getMobAiEnabled() && creature.isAlive() && !creature.closed && distance <= 144;
        }
        if (creature instanceof Player) {
            Player player = (Player) creature;
            return player.spawned && player.isAlive() && !player.closed && distance <= 49 && player.getInventory().getItemInHandFast().getId() == Item.SWEET_BERRIES;
        }
        return false;
    }

    @Override
    public boolean canTarget(Entity entity) {
        return super.canTarget(entity) || this.getServer().getMobAiEnabled() && this.getPanicTicks() <= 0
                && entity instanceof EntityCreature && this.isPrey((EntityCreature) entity) && entity.canBeFollowed();
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        this.preyAttackCooldown = Math.max(0, this.preyAttackCooldown - tickDiff);
        return super.entityBaseTick(tickDiff);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        boolean updated = super.onUpdate(currentTick);
        Entity prey = this.followTarget != null ? this.followTarget : this.target instanceof Entity ? (Entity) this.target : null;
        if (!updated || this.closed || !this.isAlive() || !this.getServer().getMobAiEnabled()
                || this.preyAttackCooldown > 0 || !(prey instanceof EntityCreature) || !this.isPrey((EntityCreature) prey)
                || this.distanceSquared(prey) > 2.25) {
            return updated;
        }
        this.preyAttackCooldown = 20;
        prey.attack(new EntityDamageByEntityEvent(this, prey,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 2));
        return updated;
    }

    @Override
    public Vector3 updateMove(int tickDiff) {
        Entity threat = this.findNearbyThreat();
        if (threat != null) {
            double dx = this.x - threat.x;
            double dz = this.z - threat.z;
            if (dx * dx + dz * dz < 0.01) {
                dx = 1;
                dz = 0;
            }
            double length = Math.sqrt(dx * dx + dz * dz);
            this.followTarget = null;
            this.target = new Vector3(this.x + dx / length * 10, this.y, this.z + dz / length * 10);
            this.stayTime = 0;
            // EntityWalking consumes moveTime in large steps while moving; keep fleeing
            // active long enough to avoid replacing the escape vector with random wandering.
            this.moveTime = 200;
            this.moveMultiplier = 1.5f;
        } else {
            this.moveMultiplier = 1.0f;
        }
        return super.updateMove(tickDiff);
    }

    private Entity findNearbyThreat() {
        long tick = this.getServer().getTick();
        if (this.lastThreatSearchTick >= 0 && tick - this.lastThreatSearchTick < 5) {
            if (this.nearbyThreat == null || !this.nearbyThreat.closed && this.nearbyThreat.isAlive()
                    && this.distanceSquared(this.nearbyThreat) <= 100) {
                return this.nearbyThreat;
            }
        }
        this.lastThreatSearchTick = tick;
        this.nearbyThreat = null;
        if (!this.getServer().getMobAiEnabled()) {
            return null;
        }
        Entity nearest = null;
        double nearestDistance = 100;
        for (Entity entity : this.level.getNearbyEntities(this.getBoundingBox().grow(10, 6, 10), this)) {
            double distance = this.distanceSquared(entity);
            if (distance >= nearestDistance) {
                continue;
            }
            if (entity instanceof Player) {
                Player player = (Player) entity;
                if (!player.closed && player.isAlive() && (player.isSurvival() || player.isAdventure())
                        && !player.isSneaking() && player.getInventory().getItemInHandFast().getId() != Item.SWEET_BERRIES) {
                    nearest = player;
                    nearestDistance = distance;
                }
            } else if (entity instanceof EntityWolf || entity instanceof EntityPolarBear) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        this.nearbyThreat = nearest;
        return this.nearbyThreat;
    }

    private boolean isPrey(EntityCreature creature) {
        return creature instanceof EntityRabbit || creature instanceof EntityChicken || creature instanceof EntityFish
                || creature instanceof EntityTurtle && ((EntityTurtle) creature).isBaby() && !creature.isInsideOfWater();
    }
}


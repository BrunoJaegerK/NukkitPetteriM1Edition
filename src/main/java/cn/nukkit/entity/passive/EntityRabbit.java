package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.BaseEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.mob.EntityMob;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Utils;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

import java.util.ArrayList;
import java.util.List;

public class EntityRabbit extends EntityJumpingAnimal {

    public static final int NETWORK_ID = 18;
    private long lastThreatSearchTick = -1;
    private Entity nearbyThreat;

    public EntityRabbit(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        if (this.isBaby()) {
            return 0.2f;
        }
        return 0.4f;
    }

    @Override
    public float getHeight() {
        if (this.isBaby()) {
            return 0.25f;
        }
        return 0.5f;
    }

    @Override
    public double getSpeed() {
        return 1.2;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(3);
        super.initEntity();
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (this.findNearbyThreat() != null) {
            return false;
        }
        if (creature instanceof Player) {
            Player player = (Player) creature;
            if (player.closed) {
                return false;
            }
            int id = player.getInventory().getItemInHandFast().getId();
            return player.spawned && player.isAlive() && (id == Item.DANDELION || id == Item.CARROT || id == Item.GOLDEN_CARROT) && distance <= 49;
        }
        return super.targetOption(creature, distance);
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
            this.target = new Vector3(this.x + dx / length * 8, this.y, this.z + dz / length * 8);
            this.stayTime = 0;
            // Preserve the escape vector across the movement timer's per-step decrement.
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
                    && this.distanceSquared(this.nearbyThreat) <= 64) {
                return this.nearbyThreat;
            }
        }
        this.lastThreatSearchTick = tick;
        this.nearbyThreat = null;
        if (!this.getServer().getMobAiEnabled()) {
            return null;
        }
        Entity nearest = null;
        double nearestDistance = 64;
        for (Entity entity : this.level.getNearbyEntities(this.getBoundingBox().grow(8, 6, 8), this)) {
            double distance = this.distanceSquared(entity);
            if (distance >= nearestDistance) {
                continue;
            }
            if (entity instanceof Player) {
                Player player = (Player) entity;
                int heldItem = player.getInventory().getItemInHandFast().getId();
                if (!player.closed && player.isAlive() && (player.isSurvival() || player.isAdventure())
                        && heldItem != Item.DANDELION && heldItem != Item.CARROT && heldItem != Item.GOLDEN_CARROT) {
                    nearest = player;
                    nearestDistance = distance;
                }
            } else if (entity instanceof EntityWolf || (entity instanceof EntityMob && entity instanceof BaseEntity
                    && !((BaseEntity) entity).isFriendly())) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        this.nearbyThreat = nearest;
        return this.nearbyThreat;
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        if (!this.isBaby()) {
            drops.add(Item.get(Item.RABBIT_HIDE, 0, Utils.rand(0, 1)));
            drops.add(Item.get(this.isOnFire() ? Item.COOKED_RABBIT : Item.RAW_RABBIT, 0, Utils.rand(0, 1)));

            for (int i = 0; i < (Utils.rand(0, 101) <= 9 ? 1 : 0); i++) {
                drops.add(Item.get(Item.RABBIT_FOOT, 0, 1));
            }
        }

        return drops.toArray(new Item[0]);
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : Utils.rand(1, 3);
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if ((item.getId() == Item.DANDELION || item.getId() == Item.CARROT || item.getId() == Item.GOLDEN_CARROT) && !this.isBaby() && !this.isInLoveCooldown()) {
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_EAT);
            this.level.addParticle(new ItemBreakParticle(this.add(0, this.getMountedYOffset(), 0), item));
            this.setInLove();
            return true;
        }
        return super.onInteract(player, item, clickedPos);
    }
}


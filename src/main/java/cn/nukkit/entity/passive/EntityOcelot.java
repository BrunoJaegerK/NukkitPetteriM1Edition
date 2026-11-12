package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

public class EntityOcelot extends EntityTameableAnimal {

    public static final int NETWORK_ID = 22;

    private long lastThreatSearchTick = -1;
    private Player nearbyThreat;

    public EntityOcelot(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        if (this.isBaby()) {
            return 0.3f;
        }
        return 0.6f;
    }

    @Override
    public float getHeight() {
        if (this.isBaby()) {
            return 0.35f;
        }
        return 0.7f;
    }

    @Override
    public double getSpeed() {
        return 1.4;
    }

    @Override
    protected void initEntity() {
        this.setMaxHealth(10);
        super.initEntity();
        this.noFallDamage = true;
    }

    @Override
    public Vector3 updateMove(int tickDiff) {
        Player threat = this.findNearbyThreat();
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
            this.moveTime = 20;
            this.moveMultiplier = 1.4f;
        } else {
            this.moveMultiplier = 1.0f;
        }
        return super.updateMove(tickDiff);
    }

    private Player findNearbyThreat() {
        if (!this.getServer().getMobAiEnabled() || this.hasOwner()) {
            this.nearbyThreat = null;
            return null;
        }

        long tick = this.getServer().getTick();
        if (this.lastThreatSearchTick >= 0 && tick - this.lastThreatSearchTick < 5) {
            if (this.nearbyThreat == null || !this.nearbyThreat.closed && this.nearbyThreat.isAlive()
                    && this.distanceSquared(this.nearbyThreat) <= 100) {
                return this.nearbyThreat;
            }
        }

        this.lastThreatSearchTick = tick;
        this.nearbyThreat = null;
        Player nearest = null;
        double nearestDistance = 100;
        for (Entity entity : this.level.getNearbyEntities(this.getBoundingBox().grow(10, 6, 10), this)) {
            if (!(entity instanceof Player)) {
                continue;
            }

            Player player = (Player) entity;
            if (player.closed || !player.isAlive() || !(player.isSurvival() || player.isAdventure())
                    || player.isSneaking()) {
                continue;
            }

            int heldItem = player.getInventory().getItemInHandFast().getId();
            if (heldItem == Item.RAW_FISH || heldItem == Item.RAW_SALMON) {
                continue;
            }

            double distance = this.distanceSquared(player);
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }

        this.nearbyThreat = nearest;
        return this.nearbyThreat;
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (creature instanceof Player) {
            Player player = (Player) creature;
            if (player.closed) {
                return false;
            }
            int id = player.getInventory().getItemInHandFast().getId();
            return player.spawned && player.isAlive() && (id == Item.RAW_FISH || id == Item.RAW_SALMON) && distance <= 49;
        }
        return super.targetOption(creature, distance);
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : Utils.rand(1, 3);
    }
}

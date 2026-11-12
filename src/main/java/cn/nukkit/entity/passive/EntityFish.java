package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

public abstract class EntityFish extends EntityWaterAnimal {

    private long lastThreatSearchTick = -1;
    private Player nearbyThreat;

    public EntityFish(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : Utils.rand(1, 3);
    }

    @Override
    public Vector3 updateMove(int tickDiff) {
        if (!(this instanceof EntityCod || this instanceof EntitySalmon || this instanceof EntityTropicalFish)
                || !this.getServer().getMobAiEnabled()) {
            this.moveMultiplier = 1.0f;
            return super.updateMove(tickDiff);
        }

        Player threat = this.findNearbyPlayerThreat();
        if (threat != null) {
            double dx = this.x - threat.x;
            double dz = this.z - threat.z;
            if (dx * dx + dz * dz < 0.01) {
                dx = 1;
                dz = 0;
            }
            double length = Math.sqrt(dx * dx + dz * dz);
            this.followTarget = null;
            this.target = new Vector3(this.x + dx / length * 6, this.y, this.z + dz / length * 6);
            this.stayTime = 0;
            this.moveTime = 200;
            this.moveMultiplier = 1.35f;
        } else {
            this.moveMultiplier = 1.0f;
        }
        return super.updateMove(tickDiff);
    }

    private Player findNearbyPlayerThreat() {
        long tick = this.getServer().getTick();
        if (this.lastThreatSearchTick >= 0 && tick - this.lastThreatSearchTick < 5) {
            if (this.nearbyThreat == null || !this.nearbyThreat.closed && this.nearbyThreat.isAlive()
                    && this.distanceSquared(this.nearbyThreat) <= 16) {
                return this.nearbyThreat;
            }
        }

        this.lastThreatSearchTick = tick;
        this.nearbyThreat = null;
        Player nearest = null;
        double nearestDistance = 16;
        for (Entity entity : this.level.getNearbyEntities(this.getBoundingBox().grow(4, 3, 4), this)) {
            if (entity instanceof Player) {
                Player player = (Player) entity;
                double distance = this.distanceSquared(player);
                if (!player.closed && player.spawned && player.isAlive()
                        && (player.isSurvival() || player.isAdventure())
                        && distance < nearestDistance) {
                    nearest = player;
                    nearestDistance = distance;
                }
            }
        }
        this.nearbyThreat = nearest;
        return nearest;
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (item.getId() == Item.BUCKET && (item.getDamage() == 0 || item.getDamage() == 8) && this.isInsideOfWater()) {
            this.close();
            if (item.getCount() <= 1) {
                player.getInventory().setItemInHand(Item.get(Item.BUCKET, this.getBucketMeta(), 1));
                return false;
            } else {
                if (!player.isCreative()) {
                    player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
                }
                player.getInventory().addItem(Item.get(Item.BUCKET, this.getBucketMeta(), 1));
                return true;
            }
        }
        return super.onInteract(player, item, clickedPos);
    }

    protected abstract int getBucketMeta();
}

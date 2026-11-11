package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Utils;

public class EntityFox extends EntityWalkingAnimal {

    public static final int NETWORK_ID = 121;

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
        if (creature instanceof Player) {
            Player player = (Player) creature;
            return player.spawned && player.isAlive() && !player.closed && distance <= 49 && player.getInventory().getItemInHandFast().getId() == Item.SWEET_BERRIES;
        }
        return false;
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
            this.moveTime = 20;
            this.moveMultiplier = 1.5f;
        } else {
            this.moveMultiplier = 1.0f;
        }
        return super.updateMove(tickDiff);
    }

    private Entity findNearbyThreat() {
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
        return nearest;
    }
}


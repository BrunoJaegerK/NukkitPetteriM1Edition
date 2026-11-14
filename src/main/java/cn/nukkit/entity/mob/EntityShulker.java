package cn.nukkit.entity.mob;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.entity.projectile.EntityShulkerBullet;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Utils;

public class EntityShulker extends EntityWalkingMob {

    public static final int NETWORK_ID = 54;

    private int peekTicks;

    public EntityShulker(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
        this.route = null;
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 1f;
    }

    @Override
    public float getHeight() {
        return 1f;
    }

    @Override
    public double getSpeed() {
        return 0;
    }

    @Override
    protected void initEntity() {
        this.setMaxHealth(15);
        super.initEntity();
        this.fireProof = true;
        this.noFallDamage = true;

        if (this.namedTag.contains("Color")) {
            this.dataProperties.putInt(DATA_VARIANT, this.namedTag.getByte("Color"));
        } else {
            this.dataProperties.putInt(DATA_VARIANT, 16);
        }

        this.setCollidable(true);
        this.setDataProperty(new IntEntityData(DATA_SHULKER_PEEK_ID, 0));
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        boolean updated = super.entityBaseTick(tickDiff);
        if (this.peekTicks > 0) {
            this.peekTicks = Math.max(0, this.peekTicks - Math.max(1, tickDiff));
            if (this.peekTicks == 0) {
                this.setDataProperty(new IntEntityData(DATA_SHULKER_PEEK_ID, 0));
            }
        }
        return updated;
    }

    @Override
    public void attackEntity(Entity player) {
        if (player == null || player.closed || !player.isAlive()) {
            return;
        }
        if (this.attackDelay > 60 && Utils.rand(1, 32) < 4 && this.distanceSquared(player) <= 256) {
            this.attackDelay = 0;

            EntityShulkerBullet shot = (EntityShulkerBullet) Entity.createEntity("ShulkerBullet", this.add(0, this.getEyeHeight(), 0), this);
            if (shot == null) {
                return;
            }

            if (shot.level.hasCollisionBlocks(shot, shot.boundingBox)) {
                shot.close();
                return;
            }

            shot.target = player;
            shot.setMotion(player.subtract(this).normalize().multiply(0.5));

            ProjectileLaunchEvent launch = new ProjectileLaunchEvent(shot);
            this.server.getPluginManager().callEvent(launch);

            if (launch.isCancelled()) {
                shot.close();
            } else {
                shot.spawnToAll();
                this.peekTicks = 20;
                this.setDataProperty(new IntEntityData(DATA_SHULKER_PEEK_ID, 100));
                this.level.addSound(this, Sound.MOB_SHULKER_SHOOT);
            }
        }
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        boolean damaged = super.attack(ev);

        if (damaged && !ev.isCancelled()) {
            if (Utils.rand(1, 10) == 1) {
                this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_TELEPORT);
                this.move(Utils.rand(-10, 10), 0, Utils.rand(-10, 10));
                this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_TELEPORT);
            }
        }

        return true;
    }

    @Override
    public Item[] getDrops() {
        return new Item[]{Item.get(Item.SHULKER_SHELL, 0, Utils.rand(0, 1))};
    }

    @Override
    public int getKillExperience() {
        return 5;
    }

    @Override
    public void knockBack(Entity attacker, double damage, double x, double z, double base) {
    }

    @Override
    public boolean canDespawn() {
        return false;
    }
}

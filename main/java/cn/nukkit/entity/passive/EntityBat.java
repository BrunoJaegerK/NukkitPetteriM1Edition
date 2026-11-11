package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;

public class EntityBat extends EntityFlyingAnimal {

    public static final int NETWORK_ID = 19;
    private boolean resting;
    private long lastRestCheckTick = -10;

    public EntityBat(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.5f;
    }

    @Override
    public float getHeight() {
        return 0.9f;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(6);
        super.initEntity();
    }

    @Override
    public int getKillExperience() {
        return 0;
    }

    @Override
    public Vector3 updateMove(int tickDiff) {
        if (!this.server.isMobAiEnabled()) {
            this.setResting(false);
            return super.updateMove(tickDiff);
        }

        long tick = this.server.getTick();
        if (tick - this.lastRestCheckTick >= 10) {
            this.lastRestCheckTick = tick;
            this.updateRestingState();
        }

        if (this.resting) {
            this.motionX = this.motionY = this.motionZ = 0;
            return null;
        }
        return super.updateMove(tickDiff);
    }

    private void updateRestingState() {
        int time = this.level.getTime() % Level.TIME_FULL;
        boolean daytime = time < 12542 || time >= 23460;
        boolean playerNearby = false;
        for (Entity entity : this.level.getNearbyEntities(this.getBoundingBox().grow(4, 4, 4), this)) {
            if (entity instanceof Player && !entity.closed && ((Player) entity).spawned) {
                playerNearby = true;
                break;
            }
        }

        if (this.resting) {
            if (!daytime || playerNearby) {
                this.setResting(false);
            }
            return;
        }

        int ceilingId = this.level.getBlockIdAt(this.chunk, this.getFloorX(), this.getFloorY() + 1, this.getFloorZ());
        if (daytime && !playerNearby && Block.isBlockSolidById(ceilingId)) {
            this.motionX = this.motionY = this.motionZ = 0;
            this.setResting(true);
        }
    }

    private void setResting(boolean resting) {
        if (this.resting == resting) {
            return;
        }
        this.resting = resting;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_RESTING, resting);
        this.moveTime = 0;
        this.stayTime = 0;
        this.target = null;
    }
}


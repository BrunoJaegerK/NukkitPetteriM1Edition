package cn.nukkit.entity.weather;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockFire;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.block.BlockIgniteEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by boybook on 2016/2/27.
 */
public class EntityLightning extends Entity implements EntityLightningStrike {

    public static final int NETWORK_ID = 93;

    protected boolean isEffect = true;

    public int state;
    public int liveTime;
    private boolean initialEffectsApplied;
    private final Set<Long> struckEntities = new HashSet<>();

    public EntityLightning(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    protected void initEntity() {
        super.initEntity();

        this.state = 2;
        this.liveTime = ThreadLocalRandom.current().nextInt(3) + 1;

    }

    private void igniteFire() {
        if (!this.isEffect || !this.level.gameRules.getBoolean(GameRule.DO_FIRE_TICK) || this.server.getDifficulty() < 2) {
            return;
        }

        Block block = this.level.getBlock(this.chunk, this.getFloorX(), this.getFloorY(), this.getFloorZ(), true);
        if (block.getId() != BlockID.AIR && block.getId() != BlockID.TALL_GRASS) {
            return;
        }

        BlockFire fire = (BlockFire) Block.get(BlockID.FIRE);
        fire.x = block.x;
        fire.y = block.y;
        fire.z = block.z;
        fire.level = this.level;

        if (fire.isBlockTopFacingSurfaceSolid(fire.down()) || fire.canNeighborBurn()) {
            BlockIgniteEvent event = new BlockIgniteEvent(block, null, this, BlockIgniteEvent.BlockIgniteCause.LIGHTNING);
            this.server.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                this.level.setBlock(fire, fire, true);
                this.level.scheduleUpdate(fire, fire.tickRate() + ThreadLocalRandom.current().nextInt(10));
            }
        }
    }

    public boolean isEffect() {
        return this.isEffect;
    }

    public void setEffect(boolean e) {
        this.isEffect = e;
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        source.setDamage(0);
        return super.attack(source);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        int tickDiff = currentTick - this.lastUpdate;

        if (tickDiff <= 0 && !this.justCreated) {
            return true;
        }

        this.lastUpdate = currentTick;

        this.entityBaseTick(tickDiff);

        // The strike event is fired before the entity is spawned. Apply the first
        // world effect on its first tick so cancelled strikes remain visual-only.
        if (!this.initialEffectsApplied) {
            this.initialEffectsApplied = true;
            this.igniteFire();
        }

        if (this.state == 2) {
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_THUNDER, -1, EntityLightning.NETWORK_ID);
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_EXPLODE, -1, EntityLightning.NETWORK_ID);
        }

        this.state--;

        if (this.state < 0) {
            if (this.liveTime == 0) {
                this.close();
                return false;
            } else if (this.state < -ThreadLocalRandom.current().nextInt(10)) {
                this.liveTime--;
                this.state = 1;

                this.igniteFire();
            }
        }

        if (this.state >= 0) {
            if (this.isEffect) {
                AxisAlignedBB bb = getBoundingBox().grow(3, 3, 3);
                for (Entity entity : this.level.getCollidingEntities(bb, this)) {
                    if (this.struckEntities.add(entity.getId())) {
                        entity.onStruckByLightning(this);
                    }
                }
            }
        }

        return true;
    }

    @Override
    public boolean canSaveToStorage() {
        return false;
    }
}

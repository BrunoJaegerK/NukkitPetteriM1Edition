package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.projectile.EntityWindCharge;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

public class EntityBreeze extends EntityFlyingMob {

    public static final int NETWORK_ID = 140;
    private static final int ATTACK_INTERVAL = 30;
    private static final double TARGET_RANGE_SQUARED = 256;
    private static final double WIND_CHARGE_SPEED = 1.5;
    private static final long LEAP_INTERVAL = 40;
    private int leapTicks;
    private int sightCheckDelay;
    private long nextLeapTick;

    public EntityBreeze(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.6f;
    }

    @Override
    public float getHeight() {
        return 1.77f;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(30);
        super.initEntity();
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        return creature instanceof Player && super.targetOption(creature, distance);
    }

    @Override
    public void attackEntity(Entity player) {
        if (!(player instanceof EntityLiving) || this.attackDelay < ATTACK_INTERVAL
                || this.distanceSquared(player) > TARGET_RANGE_SQUARED) {
            return;
        }
        if (this.sightCheckDelay > 0) {
            this.sightCheckDelay--;
            return;
        }
        this.sightCheckDelay = 5;
        if (!this.seesTarget(player)) {
            return;
        }

        Entity shotEntity = Entity.createEntity("WindCharge", this.add(0, this.getEyeHeight(), 0), this);
        if (!(shotEntity instanceof EntityWindCharge)) {
            if (shotEntity != null) {
                shotEntity.close();
            }
            return;
        }

        EntityWindCharge shot = (EntityWindCharge) shotEntity;
        Vector3 aim = player.add(0, ((EntityLiving) player).getHeight() * 0.5, 0);
        shot.setMotion(aim.subtract(shot).normalize().multiply(WIND_CHARGE_SPEED));

        this.attackDelay = 0;
        ProjectileLaunchEvent launch = new ProjectileLaunchEvent(shot);
        this.server.getPluginManager().callEvent(launch);
        if (launch.isCancelled()) {
            shot.close();
        } else {
            shot.spawnToAll();
        }
    }

    @Override
    public Vector3 updateMove(int tickDiff) {
        Vector3 target = this.followTarget != null ? this.followTarget : this.target;
        if (this.isInTickingRange() && !this.isImmobile() && target instanceof EntityCreature) {
            double distance = this.distanceSquared(target);
            long tick = this.server.getTick();
            if (this.leapTicks == 0 && tick >= this.nextLeapTick && distance > 9 && distance <= 144) {
                this.leapTicks = 5;
                this.nextLeapTick = tick + LEAP_INTERVAL;
            }
        }
        return super.updateMove(tickDiff);
    }

    @Override
    protected double getVerticalSpeed(double multiplier, double difference) {
        if (this.leapTicks > 0) {
            this.leapTicks--;
            return 0.65;
        }
        return super.getVerticalSpeed(multiplier, difference);
    }

    @Override
    public Item[] getDrops() {
        return new Item[]{Item.get(Item.BREEZE_ROD, 0, Utils.rand(1, 2))};
    }

    @Override
    public int getKillExperience() {
        return 10;
    }

    @Override
    protected int nearbyDistanceMultiplier() {
        return 4;
    }
}


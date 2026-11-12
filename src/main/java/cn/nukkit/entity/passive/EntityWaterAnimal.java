package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntitySwimming;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

public abstract class EntityWaterAnimal extends EntitySwimming implements EntityAnimal {

    private int panicTicks;

    public EntityWaterAnimal(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public double getSpeed() {
        return 0.8;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        if (!this.isAlive()) {
            if (++this.deadTicks >= 23) {
                this.close();
                return false;
            }
            return true;
        }

        int tickDiff = currentTick - this.lastUpdate;
        this.lastUpdate = currentTick;

        if (this.panicTicks > 0) {
            this.panicTicks -= tickDiff;
            if (this.panicTicks <= 0) {
                this.doPanic(false);
            }
        }

        this.entityBaseTick(tickDiff);

        Vector3 target = this.updateMove(tickDiff);
        /*if (target instanceof Player) {
            if (this.distanceSquared(target) <= 2) {
                //this.pitch = 22;
                this.x = this.lastX;
                this.y = this.lastY;
                this.z = this.lastZ;
            }
        }*/
        return true;
    }

    public void doPanic(boolean panic) {
        if (panic) {
            this.panicTicks = Utils.rand(60, 100);
            this.stayTime = 0;
            this.moveTime = this.panicTicks;
            this.moveMultiplier = 1.8f;
        } else {
            this.moveMultiplier = 1.0f;
        }
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        boolean damaged = super.attack(ev);
        if (damaged && !ev.isCancelled() && ev.getFinalDamage() > 0) {
            this.doPanic(true);
        }
        return damaged;
    }
    @Override
    public boolean canTarget(Entity entity) {
        return this.panicTicks <= 0 && (this.isInLove() || entity instanceof Player)
                && entity.canBeFollowed();
    }

    @Override
    public boolean isFriendly() {
        return true;
    }
}


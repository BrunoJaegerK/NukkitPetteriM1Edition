package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

import java.util.HashMap;

public class EntityZombieNautilus extends EntityNautilus {

    public static final int NETWORK_ID = 150;
    private static final int ANGER_DURATION = 600;
    private static final int DASH_COOLDOWN = 80;
    private static final int DASH_DURATION = 6;
    private static final double DASH_START_RANGE_SQUARED = 64;
    private static final int[] DASH_DAMAGE = {0, 2, 3, 4};

    private long angryTargetEid = -1;
    private int angryTicks;
    private Entity dashTarget;
    private int dashTicks;
    private float moveMultiplierBeforeDash = 1.0f;

    public EntityZombieNautilus(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        if (this.getServer().getDifficulty() == 0) {
            this.angryTargetEid = -1;
            this.angryTicks = 0;
            this.target = null;
            this.followTarget = null;
            this.cancelDash();
            return super.onUpdate(currentTick);
        }

        boolean updated = super.onUpdate(currentTick);
        if (this.closed) {
            return false;
        }

        if (this.angryTicks > 0 && --this.angryTicks == 0) {
            this.angryTargetEid = -1;
            this.target = null;
            this.followTarget = null;
            this.cancelDash();
        }

        if (this.getServer().getMobAiEnabled() && this.target instanceof Entity) {
            Entity currentTarget = (Entity) this.target;
            if (!currentTarget.closed && currentTarget.isAlive() && currentTarget.getId() == this.angryTargetEid) {
                this.attackEntity(currentTarget);
            }
        } else {
            this.cancelDash();
        }
        return updated;
    }

    @Override
    public boolean canTarget(Entity entity) {
        return entity.getId() == this.angryTargetEid && entity.canBeFollowed();
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        return this.angryTargetEid != -1 && creature.getId() == this.angryTargetEid
                && creature instanceof Player && !creature.closed && creature.isAlive()
                && distance <= 256;
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        boolean hasUpdate = super.entityBaseTick(tickDiff);
        this.attackDelay = Math.min(200, this.attackDelay + tickDiff);
        return hasUpdate;
    }

    @Override
    public boolean attack(EntityDamageEvent event) {
        boolean attacked = super.attack(event);
        if (attacked && !event.isCancelled() && event.getFinalDamage() > 0
                && event instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
            if (damager instanceof Player && (((Player) damager).isSurvival() || ((Player) damager).isAdventure())) {
                this.angryTargetEid = damager.getId();
                this.angryTicks = ANGER_DURATION;
                this.target = null;
                this.followTarget = null;
                this.stayTime = 0;
                this.moveTime = 0;
                this.doPanic(false);
                this.cancelDash();
            }
        }
        return attacked;
    }

    private void attackEntity(Entity target) {
        if (this.dashTarget != null) {
            if (this.dashTarget != target || target.closed || !target.isAlive()) {
                this.cancelDash();
            } else if (--this.dashTicks <= 0) {
                this.cancelDash();
                if (this.distanceSquared(target) <= 2 + this.getWidth() * this.getWidth()) {
                    this.performDashAttack(target);
                }
            }
            return;
        }

        if (this.attackDelay > DASH_COOLDOWN && this.distanceSquared(target) <= DASH_START_RANGE_SQUARED) {
            this.attackDelay = 0;
            this.dashTarget = target;
            this.dashTicks = DASH_DURATION;
            this.moveMultiplierBeforeDash = this.moveMultiplier;
            this.moveMultiplier = Math.max(this.moveMultiplier, 3.0f);
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_CHARGE_ATTACK, true);
        }
    }

    private void performDashAttack(Entity target) {
        HashMap<EntityDamageEvent.DamageModifier, Float> damage = new HashMap<>();
        int difficulty = this.getServer().getDifficulty();
        damage.put(EntityDamageEvent.DamageModifier.BASE, (float) DASH_DAMAGE[difficulty]);

        if (target instanceof Player) {
            float armorPoints = 0;
            for (Item armor : ((Player) target).getInventory().getArmorContents()) {
                armorPoints += this.getArmorPoints(armor.getId());
            }
            damage.put(EntityDamageEvent.DamageModifier.ARMOR,
                    (float) -Math.floor(DASH_DAMAGE[difficulty] * armorPoints * 0.04));
        }

        target.attack(new EntityDamageByEntityEvent(this, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage, 1.0f));
        this.playAttack();
    }

    private void cancelDash() {
        if (this.dashTarget != null) {
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_CHARGE_ATTACK, false);
        }
        this.dashTarget = null;
        this.dashTicks = 0;
        this.moveMultiplier = this.moveMultiplierBeforeDash;
    }

    @Override
    public Item[] getDrops() {
        if (!this.isBaby()) {
            return new Item[]{Item.get(Item.ROTTEN_FLESH, 0, Utils.rand(0, 3))};
        }

        return new Item[0];
    }
}


package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.potion.Effect;

import java.util.HashMap;

public class EntityWarden extends EntityWalkingMob {

    public static final int NETWORK_ID = 131;
    private static final double SONIC_BOOM_RANGE_SQUARED = 225;
    private static final double MELEE_RANGE_SQUARED = 2 + 0.9 * 0.9;
    private static final int SONIC_BOOM_CHARGE_TICKS = 34;

    private Entity sonicBoomTarget;
    private int sonicBoomChargeTicks;
    private int darknessCooldown;

    public EntityWarden(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getKillExperience() {
        return 5;
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getHeight() {
        return 2.9f;
    }

    @Override
    public float getWidth() {
        return 0.9f;
    }

    @Override
    protected float getKnockbackModifier() {
        return 0f;
    }

    @Override
    protected void initEntity() {
        this.setMaxHealth(500);
        super.initEntity();
        this.setDamage(new int[]{0, 16, 30, 45});
    }

    @Override
    public void attackEntity(Entity player) {
        double distanceSquared = this.distanceSquared(player);
        if (this.sonicBoomTarget != null) {
            if (this.sonicBoomTarget != player || player.closed || !player.isAlive()
                    || distanceSquared > SONIC_BOOM_RANGE_SQUARED) {
                this.cancelSonicBoom();
            } else if (distanceSquared >= MELEE_RANGE_SQUARED) {
                if (--this.sonicBoomChargeTicks <= 0) {
                    this.performSonicBoom(player);
                }
                return;
            }
        }

        if (distanceSquared < MELEE_RANGE_SQUARED) {
            this.cancelSonicBoom();
            if (this.attackDelay > 40) {
                this.attackDelay = 0;
                this.attackMelee(player);
            }
            return;
        }

        if (distanceSquared <= SONIC_BOOM_RANGE_SQUARED && this.attackDelay > 40) {
            this.sonicBoomTarget = player;
            this.sonicBoomChargeTicks = SONIC_BOOM_CHARGE_TICKS;
            this.attackDelay = 0;
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_SONIC_BOOM, true);
            this.level.addSound(this, Sound.MOB_WARDEN_SONIC_CHARGE);
        }
    }

    private void attackMelee(Entity player) {
        HashMap<EntityDamageEvent.DamageModifier, Float> damage = new HashMap<>();
        damage.put(EntityDamageEvent.DamageModifier.BASE, (float) this.getDamage());

        if (player instanceof Player) {
            float points = 0;
            for (Item item : ((Player) player).getInventory().getArmorContents()) {
                points += this.getArmorPoints(item.getId());
            }
            damage.put(EntityDamageEvent.DamageModifier.ARMOR,
                    (float) (damage.getOrDefault(EntityDamageEvent.DamageModifier.ARMOR, 0f)
                            - Math.floor(damage.getOrDefault(EntityDamageEvent.DamageModifier.BASE, 1f) * points * 0.04)));
        }

        player.attack(new EntityDamageByEntityEvent(this, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage));
        this.playAttack();
    }

    private void performSonicBoom(Entity target) {
        this.cancelSonicBoom();
        this.attackDelay = 0;
        this.level.addSound(this, Sound.MOB_WARDEN_SONIC_BOOM);
        this.level.addLevelEvent(target, LevelEventPacket.EVENT_PARTICLE_SONIC_EXPLOSION);

        // The sonic boom pierces blocks and bypasses armor, enchantments, and shields.
        target.attack(new EntityDamageByEntityEvent(this, target, EntityDamageEvent.DamageCause.MAGIC, 10f, 1.5f));
    }

    private void cancelSonicBoom() {
        if (this.sonicBoomTarget != null) {
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_SONIC_BOOM, false);
        }
        this.sonicBoomTarget = null;
        this.sonicBoomChargeTicks = 0;
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        boolean hasUpdate = super.entityBaseTick(tickDiff);
        if (!this.getServer().getMobAiEnabled()) {
            this.darknessCooldown = 0;
        } else {
            this.darknessCooldown += tickDiff;
            if (this.darknessCooldown >= 120) {
                this.darknessCooldown %= 120;
                for (Entity entity : this.level.getNearbyEntities(this.getBoundingBox().grow(20, 20, 20), this)) {
                    if (entity instanceof Player && entity.isAlive() && !entity.closed
                            && (((Player) entity).isSurvival() || ((Player) entity).isAdventure())
                            && this.distanceSquared(entity) <= 400) {
                        entity.addEffect(Effect.getEffect(Effect.DARKNESS).setDuration(260), EntityPotionEffectEvent.Cause.WARDEN);
                    }
                }
            }
        }
        if (!this.getServer().getMobAiEnabled() || this.sonicBoomTarget != this.followTarget
                || this.sonicBoomTarget != null && (this.sonicBoomTarget.closed || !this.sonicBoomTarget.isAlive())) {
            this.cancelSonicBoom();
        }
        return hasUpdate;
    }
}


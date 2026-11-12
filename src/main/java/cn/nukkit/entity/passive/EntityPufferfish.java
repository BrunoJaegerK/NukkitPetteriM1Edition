package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.ByteEntityData;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.Utils;

public class EntityPufferfish extends EntityFish {

    public static final int NETWORK_ID = 108;

    private int puffed = 0;
    private int puffStage = 0;
    private int contactCooldown = 0;
    private int proximityCheckTicks = 0;

    public EntityPufferfish(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected int getBucketMeta() {
        return 5;
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.35f;
    }

    @Override
    public float getHeight() {
        return 0.35f;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(3);
        super.initEntity();
    }

    @Override
    public Item[] getDrops() {
        return new Item[]{Item.get(Item.PUFFERFISH, 0, 1), Item.get(Item.BONE, 0, Utils.rand(0, 2))};
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        boolean damaged = super.attack(ev);
        if (damaged && ev instanceof EntityDamageByEntityEvent
                && ev.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && ((EntityDamageByEntityEvent) ev).getDamager() instanceof Player) {
            this.setPuffStage(2);
            this.puffed = 200;
        }
        return damaged;
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (this.getServer().getMobAiEnabled() && this.isAlive()) {
            this.proximityCheckTicks -= tickDiff;
            if (this.proximityCheckTicks <= 0) {
                this.proximityCheckTicks = 10;
                double nearestDistance = 25;
                for (Entity entity : this.level.getNearbyEntities(this.getBoundingBox().grow(5, 3, 5), this)) {
                    if (entity instanceof Player) {
                        Player player = (Player) entity;
                        double distance = this.distanceSquared(player);
                        if (!player.closed && player.isAlive()
                                && (player.isSurvival() || player.isAdventure())
                                && distance < nearestDistance) {
                            nearestDistance = distance;
                        }
                    }
                }
                if (nearestDistance < 25) {
                    this.setPuffStage(nearestDistance <= 4 ? 2 : 1);
                    this.puffed = 40;
                }
            }
        }

        if (this.puffed > 0) {
            this.puffed -= tickDiff;
            if (this.puffed <= 0) {
                this.puffed = 0;
                this.setPuffStage(Math.max(0, this.puffStage - 1));
                if (this.puffStage > 0) {
                    this.puffed = 20;
                }
            }
        }

        if (this.contactCooldown > 0) {
            this.contactCooldown = Math.max(0, this.contactCooldown - tickDiff);
        } else if (this.puffStage == 2 && this.getServer().getMobAiEnabled()) {
            for (Entity entity : this.level.getNearbyEntities(this.getBoundingBox().grow(1, 1, 1), this)) {
                if (entity instanceof Player) {
                    Player player = (Player) entity;
                    if (!player.closed && player.isAlive() && this.distanceSquared(player) <= 2.25) {
                        player.addEffect(Effect.getEffect(Effect.POISON).setDuration(60),
                                EntityPotionEffectEvent.Cause.ATTACK);
                        this.contactCooldown = 40;
                        break;
                    }
                }
            }
        }

        return super.entityBaseTick(tickDiff);
    }

    private void setPuffStage(int stage) {
        stage = Math.max(0, Math.min(2, stage));
        if (this.puffStage == stage) {
            return;
        }
        this.puffStage = stage;
        this.setDataProperty(new ByteEntityData(DATA_PUFFERFISH_SIZE, stage));
    }

    /**
     * Is puffed
     *
     * @return whether the pufferfish is puffed
     */
    public boolean isPuffed() {
        return this.puffStage > 0;
    }
}

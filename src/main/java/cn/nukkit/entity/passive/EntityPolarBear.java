package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.mob.EntityWalkingMob;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EntityPolarBear extends EntityWalkingMob {

    public static final int NETWORK_ID = 28;

    private int angry = 0;

    public EntityPolarBear(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        if (this.isBaby()) {
            return 0.65f;
        }
        return 1.3f;
    }

    @Override
    public float getHeight() {
        if (this.isBaby()) {
            return 0.7f;
        }
        return 1.4f;
    }

    @Override
    public double getSpeed() {
        return 1.25;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(30);
        super.initEntity();

        this.setDamage(new int[]{0, 4, 6, 9});

        if (!this.server.suomiCraftPEMode() && this.namedTag.contains("Angry")) {
            this.angry = this.namedTag.getInt("Angry");
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putInt("Angry", this.angry);
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (!this.getServer().getMobAiEnabled() || !creature.isAlive() || creature.closed || distance > 256) {
            return false;
        }
        if (!this.isBaby() && creature instanceof EntityFox) {
            this.setAngry(500);
            return super.targetOption(creature, distance);
        }
        if (!this.isBaby() && creature instanceof Player && this.isPlayerThreateningNearbyCub((Player) creature)) {
            this.setAngry(500);
        }
        return this.isAngry() && super.targetOption(creature, distance);
    }

    @Override
    public Vector3 updateMove(int tickDiff) {
        Player threat = this.isBaby() && this.getServer().getMobAiEnabled() ? this.findNearbyPlayerThreat() : null;
        if (threat != null) {
            double dx = this.x - threat.x;
            double dz = this.z - threat.z;
            if (dx * dx + dz * dz < 0.01) {
                dx = 1;
                dz = 0;
            }
            double length = Math.sqrt(dx * dx + dz * dz);
            this.followTarget = null;
            this.target = new Vector3(this.x + dx / length * 8, this.y, this.z + dz / length * 8);
            this.stayTime = 0;
            this.moveTime = 20;
            this.moveMultiplier = 1.25f;
        } else {
            this.moveMultiplier = 1.0f;
        }
        return super.updateMove(tickDiff);
    }

    private Player findNearbyPlayerThreat() {
        Player nearest = null;
        double nearestDistance = 256;
        for (Entity entity : this.level.getNearbyEntities(this.getBoundingBox().grow(16, 8, 16), this)) {
            if (entity instanceof Player) {
                Player player = (Player) entity;
                if (!player.closed && player.isAlive() && (player.isSurvival() || player.isAdventure())) {
                    double distance = this.distanceSquared(player);
                    if (distance < nearestDistance) {
                        nearest = player;
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }

    private boolean isPlayerThreateningNearbyCub(Player player) {
        if (!(player.isSurvival() || player.isAdventure()) || player.closed || !player.isAlive()) {
            return false;
        }
        for (Entity entity : this.level.getNearbyEntities(this.getBoundingBox().grow(41, 16, 41), this)) {
            if (entity instanceof EntityPolarBear && ((EntityPolarBear) entity).isBaby()
                    && entity.distanceSquared(player) <= 256) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.attackDelay > 23 && this.distanceSquared(player) < 2 + this.getWidth() * this.getWidth()) {
            this.attackDelay = 0;
            HashMap<EntityDamageEvent.DamageModifier, Float> damage = new HashMap<>();
            damage.put(EntityDamageEvent.DamageModifier.BASE, (float) this.getDamage());

            if (player instanceof Player) {
                float points = 0;
                for (Item i : ((Player) player).getInventory().getArmorContents()) {
                    points += this.getArmorPoints(i.getId());
                }

                damage.put(EntityDamageEvent.DamageModifier.ARMOR,
                        (float) (damage.getOrDefault(EntityDamageEvent.DamageModifier.ARMOR, 0f) - Math.floor(damage.getOrDefault(EntityDamageEvent.DamageModifier.BASE, 1f) * points * 0.04)));
            }
            player.attack(new EntityDamageByEntityEvent(this, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage));
        }
    }

    public boolean isAngry() {
        return this.angry > 0;
    }

    public void setAngry(int val) {
        this.angry = val;
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        boolean damaged = super.attack(ev);
        if (damaged && ev instanceof EntityDamageByEntityEvent
                && ((EntityDamageByEntityEvent) ev).getDamager() instanceof Player) {
            this.setAngry(2400);
        }
        return damaged;
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        if (!this.isBaby()) {
            drops.add(Item.get(Item.RAW_FISH, 0, Utils.rand(0, 2)));
            drops.add(Item.get(Item.RAW_SALMON, 0, Utils.rand(0, 2)));
        }

        return drops.toArray(new Item[0]);
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : Utils.rand(1, 3);
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.getNameTag() : "Polar Bear";
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (this.angry > 0) {
            this.angry--;
        }

        return super.entityBaseTick(tickDiff);
    }

    @Override
    protected float getFreezingDamage() {
        return 0f;
    }
}


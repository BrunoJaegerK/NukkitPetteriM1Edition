package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.BaseEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.block.Block;
import cn.nukkit.event.entity.CreatureSpawnEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static cn.nukkit.block.BlockID.NETHER_PORTAL;
import static cn.nukkit.block.BlockID.RESPAWN_ANCHOR;
import static cn.nukkit.block.BlockID.WARPED_FUNGUS;

/**
 * @author Erik Miller | EinBexiii
 */
public class EntityHoglin extends EntityWalkingMob {

    public final static int NETWORK_ID = 124;

    private static final int REPELLENT_RANGE = 7;
    private static final int ZOMBIFICATION_TICKS = 300;
    private static final String NBT_KEY_OUTSIDE_NETHER_TICKS = "HoglinOutsideNetherTicks";
    private Vector3 repellentPosition;
    private int repellentSearchCooldown;
    private int outsideNetherTicks;

    public EntityHoglin(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : Utils.rand(1, 3);
    }

    @Override
    protected void initEntity() {
        this.setMaxHealth(40);
        super.initEntity();
        this.setDamage(new int[]{0, 2, 3, 4});
        this.outsideNetherTicks = Math.max(0, this.namedTag.getInt(NBT_KEY_OUTSIDE_NETHER_TICKS));
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putInt(NBT_KEY_OUTSIDE_NETHER_TICKS, this.outsideNetherTicks);
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (this.level.getDimension() == Level.DIMENSION_NETHER) {
            this.outsideNetherTicks = 0;
        } else {
            this.outsideNetherTicks = Math.min(ZOMBIFICATION_TICKS, this.outsideNetherTicks + tickDiff);
        }
        return super.entityBaseTick(tickDiff);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        boolean updated = super.onUpdate(currentTick);
        if (updated && !this.closed && this.isAlive() && this.outsideNetherTicks >= ZOMBIFICATION_TICKS) {
            CreatureSpawnEvent event = new CreatureSpawnEvent(EntityZoglin.NETWORK_ID, this,
                    CreatureSpawnEvent.SpawnReason.DEFAULT);
            this.server.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                Entity zoglin = Entity.createEntity(EntityZoglin.NETWORK_ID, this.getChunk(), Entity.getDefaultNBT(this));
                if (zoglin != null) {
                    ((BaseEntity) zoglin).setBaby(this.isBaby());
                    zoglin.setHealth(Math.min(zoglin.getMaxHealth(), this.getHealth()));
                    this.close();
                    zoglin.spawnToAll();
                    return false;
                }
            }
        }
        return updated;
    }

    @Override
    public float getWidth() {
        return 0.9f;
    }

    @Override
    public float getHeight() {
        return 0.9f;
    }

    @Override
    protected float getKnockbackModifier() {
        return 0.4f;
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.attackDelay > 23 && player.distanceSquared(this) < 2 + this.getWidth() * this.getWidth()) {
            this.attackDelay = 0;
            HashMap<EntityDamageEvent.DamageModifier, Float> damage = new HashMap<>();
            damage.put(EntityDamageEvent.DamageModifier.BASE, (float) this.getDamage());

            if (player instanceof Player) {
                float points = 0;
                for (Item i : ((Player) player).getInventory().getArmorContents()) {
                    points += this.getArmorPoints(i.getId());
                }

                damage.put(EntityDamageEvent.DamageModifier.ARMOR, (float) (damage.getOrDefault(EntityDamageEvent.DamageModifier.ARMOR, 0f) - Math.floor(damage.getOrDefault(EntityDamageEvent.DamageModifier.BASE, 1f) * points * 0.04)));
            }
            player.attack(new EntityDamageByEntityEvent(this, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage));
        }
    }

    @Override
    public Vector3 updateMove(int tickDiff) {
        if (!this.getServer().getMobAiEnabled()) {
            this.repellentPosition = null;
            return super.updateMove(tickDiff);
        }
        this.repellentSearchCooldown -= tickDiff;
        if (this.repellentSearchCooldown <= 0) {
            this.repellentSearchCooldown = 10;
            this.repellentPosition = this.findNearbyRepellent();
        }
        if (this.repellentPosition != null) {
            double dx = this.x - (this.repellentPosition.x + 0.5);
            double dz = this.z - (this.repellentPosition.z + 0.5);
            if (dx * dx + dz * dz < 0.01) {
                dx = 1;
                dz = 0;
            }
            double length = Math.sqrt(dx * dx + dz * dz);
            this.followTarget = null;
            this.target = new Vector3(this.x + dx / length * 10, this.y, this.z + dz / length * 10);
            this.stayTime = 0;
            this.moveTime = 20;
            this.moveMultiplier = 1.3f;
        } else {
            this.moveMultiplier = 1.0f;
        }
        return super.updateMove(tickDiff);
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (this.repellentPosition != null && this.distanceSquared(this.repellentPosition) <= REPELLENT_RANGE * REPELLENT_RANGE) {
            return false;
        }
        return super.targetOption(creature, distance);
    }

    private Vector3 findNearbyRepellent() {
        Vector3 nearest = null;
        double nearestDistance = REPELLENT_RANGE * REPELLENT_RANGE;
        int centerX = this.getFloorX();
        int centerY = this.getFloorY();
        int centerZ = this.getFloorZ();
        for (int x = centerX - REPELLENT_RANGE; x <= centerX + REPELLENT_RANGE; x++) {
            for (int z = centerZ - REPELLENT_RANGE; z <= centerZ + REPELLENT_RANGE; z++) {
                FullChunk chunk = this.level.getChunkIfLoaded(x >> 4, z >> 4);
                if (chunk == null) {
                    continue;
                }
                for (int y = Math.max(this.level.getMinBlockY(), centerY - 2);
                     y <= Math.min(this.level.getMaxBlockY() - 1, centerY + 3); y++) {
                    double dx = x + 0.5 - this.x;
                    double dy = y + 0.5 - this.y;
                    double dz = z + 0.5 - this.z;
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance > nearestDistance) {
                        continue;
                    }
                    int id = chunk.getBlockId(x, y, z, Block.LAYER_NORMAL);
                    if (id == WARPED_FUNGUS || id == NETHER_PORTAL || id == RESPAWN_ANCHOR) {
                        nearest = new Vector3(x, y, z);
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        if (!this.isBaby()) {
            for (int i = 0; i < Utils.rand(2, 4); i++) {
                drops.add(Item.get(this.isOnFire() ? Item.COOKED_PORKCHOP : Item.RAW_PORKCHOP, 0, 1));
            }

            if (Utils.rand()) {
                drops.add(Item.get(Item.LEATHER));
            }
        }

        return drops.toArray(new Item[0]);
    }
}


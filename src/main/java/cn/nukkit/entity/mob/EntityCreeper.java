package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.EntityExplosive;
import cn.nukkit.entity.passive.EntityCat;
import cn.nukkit.entity.passive.EntityOcelot;
import cn.nukkit.entity.weather.EntityLightningStrike;
import cn.nukkit.event.entity.CreeperPowerEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityExplosionPrimeEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSkull;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class EntityCreeper extends EntityWalkingMob implements EntityExplosive {

    public static final int NETWORK_ID = 33;

    private short bombTime;
    private int explodeTimer; // When ignited by player
    private long seenTarget = -1L;
    private Entity catThreat;
    private int catSearchCooldown;

    public EntityCreeper(FullChunk chunk, CompoundTag nbt) {
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
        return 1.7f;
    }

    @Override
    public double getSpeed() {
        return 0.9;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(20);
        super.initEntity();

        if (this.namedTag.contains("powered")) {
            this.setPowered(this.namedTag.getBoolean("powered"));
        }
    }

    public void explode() {
        if (this.closed) return;

        EntityExplosionPrimeEvent ev = new EntityExplosionPrimeEvent(this, this.isPowered() ? 6 : 3);
        this.server.getPluginManager().callEvent(ev);

        if (!ev.isCancelled()) {
            Explosion explosion = new Explosion(this, (float) ev.getForce(), this);

            if (ev.isBlockBreaking() && this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING)) {
                explosion.explodeA();
            }

            explosion.explodeB();
        }

        this.close();
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        if (this.server.getDifficulty() < 1) {
            this.close();
            return false;
        }

        if (!this.isAlive()) {
            if (++this.deadTicks >= 23) {
                this.close();
                return false;
            }
            return true;
        }

        if (this.explodeTimer > 0) {
            if (this.explodeTimer == 1) {
                this.explode();
                return false;
            }
            this.explodeTimer--;
        }

        int tickDiff = currentTick - this.lastUpdate;
        this.lastUpdate = currentTick;
        this.entityBaseTick(tickDiff);

        Vector3 target = this.updateMove(tickDiff);
        if (this.getServer().getMobAiEnabled() && target != null) {
            double distance = target.distanceSquared(this);

            long targetId = target instanceof EntityCreature ? ((EntityCreature) target).getId() : -1L;
            if (targetId != this.seenTarget) {
                this.seenTarget = -1L;
            }

            if (targetId != -1L && this.seenTarget == -1L && distance <= 16 && this.seesTarget(target)) {
                this.seenTarget = targetId;
            }

            if (distance <= 16 && targetId != -1L && this.seenTarget == targetId) { // 4 blocks
                if (this.explodeTimer <= 0) {
                    if (bombTime == 0) {
                        this.getLevel().addLevelEvent(this, LevelEventPacket.EVENT_SOUND_TNT);
                        this.setDataFlag(DATA_FLAGS, DATA_FLAG_IGNITED, true);
                    }
                    this.bombTime += tickDiff;
                    if (this.bombTime >= 30) {
                        this.explode();
                        return false;
                    }
                }
                if (distance <= 1) {
                    this.stayTime = 10;
                }
            } else {
                if (this.explodeTimer <= 0) {
                    this.setDataFlag(DATA_FLAGS, DATA_FLAG_IGNITED, false);
                    this.bombTime = 0;
                }
            }
        } else if (this.explodeTimer <= 0) {
            this.seenTarget = -1L;
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_IGNITED, false);
            this.bombTime = 0;
        }
        return true;
    }

    @Override
    public Vector3 updateMove(int tickDiff) {
        if (this.getServer().getMobAiEnabled()) {
            this.catSearchCooldown -= tickDiff;
            if (this.catSearchCooldown <= 0) {
                this.catThreat = this.findNearbyCat();
                this.catSearchCooldown = 5;
            }
            if (this.catThreat != null && (this.catThreat.closed || !this.catThreat.isAlive()
                    || this.distanceSquared(this.catThreat) > 36)) {
                this.catThreat = null;
                this.target = null;
                this.followTarget = null;
            }
            if (this.catThreat != null) {
                this.followTarget = null;
                this.target = this.getFleeTarget(this.catThreat);
                this.stayTime = 0;
                this.moveTime = 20;
            }
        }
        return super.updateMove(tickDiff);
    }

    @Override
    protected void checkTarget() {
        super.checkTarget();
        if (this.getServer().getMobAiEnabled() && this.catThreat != null && !this.catThreat.closed
                && this.catThreat.isAlive() && this.distanceSquared(this.catThreat) <= 36) {
            this.followTarget = null;
            this.target = this.getFleeTarget(this.catThreat);
            this.stayTime = 0;
        }
    }

    private Entity findNearbyCat() {
        Entity nearest = null;
        double nearestDistance = 36;
        for (Entity entity : this.level.getNearbyEntities(this.getBoundingBox().grow(6, 6, 6), this)) {
            if (!(entity instanceof EntityCat || entity instanceof EntityOcelot) || entity.closed || !entity.isAlive()) {
                continue;
            }
            double distance = this.distanceSquared(entity);
            if (distance < nearestDistance) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private Vector3 getFleeTarget(Entity threat) {
        double dx = this.x - threat.x;
        double dz = this.z - threat.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.001) {
            dx = 1;
            distance = 1;
        }
        return this.add(dx / distance * 8, 0, dz / distance * 8);
    }

    @Override
    public void attackEntity(Entity player) {
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        for (int i = 0; i < Utils.rand(0, 2); i++) {
            drops.add(Item.get(Item.GUNPOWDER, 0, 1));
        }

        return drops.toArray(new Item[0]);
    }

    @Override
    public int getKillExperience() {
        return 5;
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (item.getId() == Item.FLINT_AND_STEEL && this.explodeTimer <= 0) {
            level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_IGNITE);
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_IGNITED, true);
            this.getLevel().addLevelEvent(this, LevelEventPacket.EVENT_SOUND_TNT);
            this.stayTime = 31;
            this.explodeTimer = 31; // Explodes at 1
            return true;
        }

        return super.onInteract(player, item, clickedPos);
    }

    public boolean isPowered() {
        return this.getDataFlag(DATA_FLAGS, DATA_FLAG_POWERED);
    }

    public void setPowered(boolean charged) {
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_POWERED, charged);
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        this.namedTag.putBoolean("powered", this.isPowered());
    }

    @Override
    public void onStruckByLightning(Entity lightning) {
        if (this.attack(new EntityDamageByEntityEvent(lightning, this, EntityDamageEvent.DamageCause.LIGHTNING, 5))) {
            if (this.fireTicks < 160) {
                this.setOnFire(8);
            }

            if (lightning instanceof EntityLightningStrike) {
                CreeperPowerEvent event = new CreeperPowerEvent(this, (EntityLightningStrike) lightning, CreeperPowerEvent.PowerCause.LIGHTNING);
                server.getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    this.setPowered(true);
                }
            }
        }
    }

    @Override
    public boolean ignoredAsSaveReason() {
        return !this.isPowered() && super.ignoredAsSaveReason();
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (creature instanceof Player) {
            Player player = (Player) creature;
            if (!player.closed && player.spawned && player.isAlive() && (player.isSurvival() || player.isAdventure())) {
                PlayerInventory inv = player.getInventory();
                Item helmet;
                if (inv != null && (helmet = inv.getHelmetFast()).getId() == Item.SKULL && helmet.getDamage() == ItemSkull.CREEPER_HEAD) {
                    return distance <= 64;
                }
                return distance <= 256;
            }
            return false;
        }
        return creature.isAlive() && !creature.closed && distance <= 256;
    }
}


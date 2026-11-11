package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityBoss;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.item.EntityEndCrystal;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.Utils;

import java.util.HashMap;

public class EntityEnderDragon extends EntityFlyingMob implements EntityBoss {

    public static final int NETWORK_ID = 53;
    private int crystalHealTick;
    private double homeX;
    private double homeY;
    private double homeZ;
    private Vector3 orbitTarget;

    public EntityEnderDragon(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
        this.homeX = this.namedTag.contains("DragonHomeX") ? this.namedTag.getDouble("DragonHomeX") : this.x;
        this.homeY = this.namedTag.contains("DragonHomeY") ? this.namedTag.getDouble("DragonHomeY") : this.y;
        this.homeZ = this.namedTag.contains("DragonHomeZ") ? this.namedTag.getDouble("DragonHomeZ") : this.z;
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 16f;
    }

    @Override
    public float getHeight() {
        return 8f;
    }

    @Override
    public double getSpeed() {
        return 3;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(200);
        super.initEntity();

        this.fireProof = true;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_FIRE_IMMUNE, true);
        this.setDamage(new int[]{0, 6, 10, 15});
    }

    @Override
    public int getKillExperience() {
        for (int i = 0; i < 167; ) {
            this.level.dropExpOrb(this, 3);
            i++;
        }
        return 0;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putDouble("DragonHomeX", this.homeX);
        this.namedTag.putDouble("DragonHomeY", this.homeY);
        this.namedTag.putDouble("DragonHomeZ", this.homeZ);
    }

    @Override
    protected void checkTarget() {
        super.checkTarget();
        if (this.target instanceof Entity) {
            this.orbitTarget = null;
            return;
        }

        if (this.orbitTarget == null || this.distanceSquared(this.orbitTarget) <= 144) {
            double angle = Utils.rand(0, 359) * Math.PI / 180.0;
            double radius = Utils.rand(24, 40);
            this.orbitTarget = new Vector3(this.homeX + Math.cos(angle) * radius,
                    this.homeY + Utils.rand(-6, 10), this.homeZ + Math.sin(angle) * radius);
            this.moveTime = 120;
            this.stayTime = 0;
        }
        this.target = this.orbitTarget;
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (!(creature instanceof Player) || this.server.getDifficulty() == 0) {
            return false;
        }
        Player player = (Player) creature;
        return !player.closed && player.spawned && player.isAlive()
                && (player.isSurvival() || player.isAdventure()) && distance <= 4096;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        // Unlike ordinary hostile mobs, the dragon remains in the End on Peaceful.
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
        if (tickDiff <= 0 && !this.justCreated) {
            return false;
        }
        this.lastUpdate = currentTick;
        this.entityBaseTick(tickDiff);

        Vector3 activeTarget = null;
        if (this.getServer().getMobAiEnabled()) {
            activeTarget = this.updateMove(tickDiff);
            healFromNearbyCrystal(tickDiff);
            if (activeTarget instanceof Entity && !this.closed) {
                this.attackEntity((Entity) activeTarget);
            }
        } else {
            this.motionX = this.motionY = this.motionZ = 0;
            this.target = null;
            this.followTarget = null;
        }
        return true;
    }

    private void healFromNearbyCrystal(int tickDiff) {
        this.crystalHealTick += tickDiff;
        if (this.crystalHealTick < 20) {
            return;
        }
        this.crystalHealTick %= 20;
        if (this.getHealth() >= this.getMaxHealth()) {
            return;
        }
        for (Entity entity : this.level.getNearbyEntities(this.getBoundingBox().grow(32, 32, 32), this)) {
            if (entity instanceof EntityEndCrystal && this.distanceSquared(entity) <= 1024) {
                this.heal(1f);
                return;
            }
        }
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.getNameTag() : "Ender Dragon";
    }

    @Override
    protected DataPacket createAddEntityPacket() {
        AddEntityPacket addEntity = new AddEntityPacket();
        addEntity.type = NETWORK_ID;
        addEntity.entityUniqueId = this.getId();
        addEntity.entityRuntimeId = this.getId();
        addEntity.yaw = (float) this.yaw;
        addEntity.headYaw = (float) this.yaw;
        addEntity.pitch = (float) this.pitch;
        addEntity.x = (float) this.x;
        addEntity.y = (float) this.y;
        addEntity.z = (float) this.z;
        addEntity.speedX = (float) this.motionX;
        addEntity.speedY = (float) this.motionY;
        addEntity.speedZ = (float) this.motionZ;
        addEntity.metadata = this.dataProperties.clone();
        addEntity.attributes = new Attribute[]{Attribute.getAttribute(Attribute.MAX_HEALTH).setMaxValue(200).setValue(200)};
        return addEntity;
    }

    @Override
    public void knockBack(Entity attacker, double damage, double x, double z, double base) {
    }

    @Override
    protected boolean applyNameTag(Player player, Item nameTag) {
        return false;
    }

    @Override
    public boolean canDespawn() {
        return false;
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.attackDelay > 20 && this.distanceSquared(player) <= 64) {
            this.attackDelay = 0;
            HashMap<EntityDamageEvent.DamageModifier, Float> damage = new HashMap<>();
            damage.put(EntityDamageEvent.DamageModifier.BASE, (float) this.getDamage());
            player.attack(new EntityDamageByEntityEvent(this, player,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage));
        }
    }
}


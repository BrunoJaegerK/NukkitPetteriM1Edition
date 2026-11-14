package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityBoss;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.item.EntityEndCrystal;
import cn.nukkit.entity.projectile.EntityEnderCharge;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
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
    private Player combatTarget;
    private int flightPhaseTicks;

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
        // Let the shared target selector choose valid players, but keep the dragon's
        // movement target separate so it circles/strafe-flies instead of walking
        // directly into the player like an ordinary flying mob.
        super.checkTarget();
        if (this.target instanceof Player) {
            Player selectedTarget = (Player) this.target;
            if (this.combatTarget != selectedTarget) {
                this.combatTarget = selectedTarget;
                this.orbitTarget = null;
                this.flightPhaseTicks = 0;
            }
        }

        if (this.combatTarget != null && (this.combatTarget.closed || !this.combatTarget.isAlive()
                || !this.combatTarget.spawned || !(this.combatTarget.isSurvival() || this.combatTarget.isAdventure())
                || this.distanceSquared(this.combatTarget) > 4096)) {
            this.combatTarget = null;
            this.orbitTarget = null;
            this.flightPhaseTicks = 0;
        }

        if (this.orbitTarget == null || this.flightPhaseTicks <= 0 || this.distanceSquared(this.orbitTarget) <= 144) {
            double angle = Utils.rand(0, 359) * Math.PI / 180.0;
            double radius;
            double centerX;
            double centerY;
            double centerZ;

            if (this.combatTarget != null) {
                // Strafing pass: circle the target at a changing radius and height.
                radius = Utils.rand(18, 30);
                centerX = this.combatTarget.x;
                centerY = this.combatTarget.y + Utils.rand(8, 18);
                centerZ = this.combatTarget.z;
                this.flightPhaseTicks = Utils.rand(60, 100);
            } else {
                // No player in range: patrol around the dragon's saved home point.
                radius = Utils.rand(24, 40);
                centerX = this.homeX;
                centerY = this.homeY + Utils.rand(-6, 10);
                centerZ = this.homeZ;
                this.flightPhaseTicks = Utils.rand(80, 140);
            }

            this.orbitTarget = new Vector3(centerX + Math.cos(angle) * radius,
                    centerY, centerZ + Math.sin(angle) * radius);
            this.moveTime = this.flightPhaseTicks;
            this.stayTime = 0;
        }

        this.followTarget = null;
        this.target = this.orbitTarget;
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (!(creature instanceof Player)) {
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

        if (this.getServer().getMobAiEnabled()) {
            this.flightPhaseTicks = Math.max(0, this.flightPhaseTicks - tickDiff);
            this.updateMove(tickDiff);
            this.healFromNearbyCrystal(tickDiff);
            if (this.combatTarget != null && !this.combatTarget.closed && this.combatTarget.isAlive() && !this.closed) {
                this.attackEntity(this.combatTarget);
            }
        } else {
            this.motionX = this.motionY = this.motionZ = 0;
            this.target = null;
            this.followTarget = null;
            this.combatTarget = null;
            this.updateMovement();
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
        double distance = this.distanceSquared(player);
        if (distance > 64 && distance <= 4096 && this.attackDelay > 100 && this.seesTarget(player)) {
            this.attackDelay = 0;
            EntityEnderCharge fireball = (EntityEnderCharge) Entity.createEntity("EnderCharge",
                    this.add(0, this.getEyeHeight(), 0), this);
            if (fireball == null || fireball.level.hasCollisionBlocks(fireball, fireball.boundingBox)) {
                if (fireball != null) {
                    fireball.close();
                }
                return;
            }
            fireball.setMotion(player.add(0, player.getEyeHeight(), 0).subtract(fireball).normalize().multiply(1));
            ProjectileLaunchEvent launch = new ProjectileLaunchEvent(fireball);
            this.server.getPluginManager().callEvent(launch);
            if (launch.isCancelled()) {
                fireball.close();
            } else {
                fireball.spawnToAll();
            }
        } else if (this.attackDelay > 20 && distance <= 64) {
            this.attackDelay = 0;
            HashMap<EntityDamageEvent.DamageModifier, Float> damage = new HashMap<>();
            float baseDamage = (float) this.getDamage();
            damage.put(EntityDamageEvent.DamageModifier.BASE, baseDamage);
            if (player instanceof Player) {
                float armorPoints = 0;
                for (Item armor : ((Player) player).getInventory().getArmorContents()) {
                    armorPoints += this.getArmorPoints(armor.getId());
                }
                damage.put(EntityDamageEvent.DamageModifier.ARMOR,
                        (float) -Math.floor(baseDamage * armorPoints * 0.04));
            }
            player.attack(new EntityDamageByEntityEvent(this, player,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage));
        }
    }
}


package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.*;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.entity.data.LongEntityData;
import cn.nukkit.entity.projectile.EntityBlueWitherSkull;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.entity.projectile.EntityWitherSkull;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageByChildEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityExplosionPrimeEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.utils.Utils;

import java.util.concurrent.ThreadLocalRandom;

public class EntityWither extends EntityFlyingMob implements EntityBoss, EntitySmite {

    public static final int NETWORK_ID = 52;
    private static final double TARGET_RANGE_SQUARED = 1600;
    private static final int SIDE_HEADS = 2;

    private boolean exploded;
    private boolean wasExplosion;
    private boolean halfHealthPhase;
    private boolean witherArmor;
    private int sideHeadCooldowns[] = {0, 0};
    private int sideHeadTargetIds[] = {0, 0};
    private EntityCreature sideHeadTargets[] = {null, null};
    private int sideHeadBlueCounters[] = {0, 0};
    private int mainHeadVolley;
    private int dashCooldown;
    private int dashTicks;
    private Entity dashTarget;

    public EntityWither(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.9f;
    }

    @Override
    public float getHeight() {
        return 3.5f;
    }

    @Override
    public double getSpeed() {
        return this.halfHealthPhase ? 1.65 : 1.3;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(witherMaxHealth());
        super.initEntity();
        this.fireProof = true;
        this.setDamage(new int[]{0, 2, 4, 6});
        this.halfHealthPhase = this.namedTag.getBoolean("WitherHalfHealthPhase");
        this.witherArmor = this.namedTag.getBoolean("WitherArmor");
        if (this.age == 0) {
            this.setDataProperty(new IntEntityData(DATA_WITHER_INVULNERABLE_TICKS, 220));
            this.stayTime = 220;
        }
        this.setDataProperty(new LongEntityData(DATA_WITHER_TARGET_1, 0));
        this.setDataProperty(new LongEntityData(DATA_WITHER_TARGET_2, 0));
        this.setDataProperty(new LongEntityData(DATA_WITHER_TARGET_3, 0));
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (creature instanceof Player) {
            Player player = (Player) creature;
            if (!player.isSurvival() && !player.isAdventure()) {
                return false;
            }
        }
        return creature.isAlive() && !creature.closed && distance <= TARGET_RANGE_SQUARED;
    }

    @Override
    public boolean canTarget(Entity entity) {
        return entity != this && entity.canBeFollowed() && !(entity instanceof EntitySmite);
    }

    @Override
    public int getKillExperience() {
        return 50;
    }

    @Override
    public void attackEntity(Entity entity) {
        if (this.age <= 220 || this.attackDelay <= getMainAttackInterval() || this.distanceSquared(entity) > TARGET_RANGE_SQUARED || !this.seesTarget(entity)) {
            return;
        }
        this.attackDelay = 0;
        this.mainHeadVolley++;
        boolean blue = this.getServer().getDifficulty() >= 2 && this.mainHeadVolley % 4 == 0;
        this.fireSkull(entity, 0, blue, blue ? 1.0 : 1.2);
    }

    private int getMainAttackInterval() {
        double healthRatio = this.getHealth() / this.getMaxHealth();
        if (healthRatio <= 1.0 / 6.0) return 12;
        if (healthRatio <= 1.0 / 3.0) return 16;
        if (healthRatio <= 0.5) return 22;
        return 35;
    }

    private void fireSkull(Entity target, int head, boolean blue, double speed) {
        if (target == null || target.closed || !target.isAlive()) return;
        double offsetX = 0;
        double offsetZ = 0;
        if (head == 1 || head == 2) {
            double angle = Math.toRadians(this.yaw + (head == 1 ? 90 : -90));
            offsetX = Math.cos(angle) * 1.15;
            offsetZ = Math.sin(angle) * 1.15;
        }
        Vector3 origin = this.add(offsetX, head == 0 ? this.getEyeHeight() + 0.7 : 2.2, offsetZ);
        Entity projectile = Entity.createEntity(blue ? "BlueWitherSkull" : "WitherSkull", origin, this);
        if (!(projectile instanceof EntityWitherSkull)) return;
        EntityWitherSkull skull = (EntityWitherSkull) projectile;
        if (blue && skull instanceof EntityBlueWitherSkull) {
            ((EntityBlueWitherSkull) skull).setExplode(true);
        }
        Vector3 aim = new Vector3(target.x, target.y + target.getEyeHeight() * 0.5, target.z).subtract(origin).normalize().multiply(speed);
        skull.setMotion(aim);
        if (skull.level.hasCollisionBlocks(skull, skull.boundingBox)) {
            skull.close();
            return;
        }
        ProjectileLaunchEvent launch = new ProjectileLaunchEvent(skull);
        this.server.getPluginManager().callEvent(launch);
        if (launch.isCancelled()) {
            skull.close();
        } else {
            skull.spawnToAll();
            this.level.addSound(this, Sound.MOB_WITHER_SHOOT);
        }
    }

    private EntityCreature getMainHeadTarget() {
        return this.followTarget instanceof EntityCreature ? (EntityCreature) this.followTarget
                : this.target instanceof EntityCreature ? (EntityCreature) this.target : null;
    }

    private void updateSideHeads(int tickDiff) {
        EntityCreature mainTarget = getMainHeadTarget();
        for (int i = 0; i < SIDE_HEADS; i++) {
            if (this.sideHeadCooldowns[i] > 0) {
                this.sideHeadCooldowns[i] = Math.max(0, this.sideHeadCooldowns[i] - tickDiff);
            }
            EntityCreature selected = this.sideHeadTargets[i];
            if (!isValidSideTarget(selected) || selected == mainTarget || !this.seesTarget(selected)) {
                selected = null;
                this.sideHeadTargets[i] = null;
                this.sideHeadTargetIds[i] = 0;
                this.setDataProperty(new LongEntityData(i == 0 ? DATA_WITHER_TARGET_2 : DATA_WITHER_TARGET_3, 0));
                // Only rescan the entity list when this head is ready to shoot.
                if (this.sideHeadCooldowns[i] == 0) {
                    selected = findSideHeadTarget(i);
                    this.sideHeadTargets[i] = selected;
                    this.sideHeadTargetIds[i] = selected == null ? 0 : (int) selected.getId();
                    this.setDataProperty(new LongEntityData(i == 0 ? DATA_WITHER_TARGET_2 : DATA_WITHER_TARGET_3, selected == null ? 0 : selected.getId()));
                }
            }
            if (this.sideHeadCooldowns[i] == 0) {
                if (selected != null) {
                    this.sideHeadBlueCounters[i] = 0;
                    this.fireSkull(selected, i + 1, false, 1.05);
                    this.sideHeadCooldowns[i] = Utils.rand(40, 60);
                } else {
                    this.sideHeadBlueCounters[i]++;
                    if (this.getServer().getDifficulty() >= 2 && this.sideHeadBlueCounters[i] >= 16) {
                        this.fireRandomBlueSkull(i + 1);
                        this.sideHeadBlueCounters[i] = 0;
                    }
                    this.sideHeadCooldowns[i] = Utils.rand(10, 19);
                }
            }
        }
    }

    private boolean isValidSideTarget(EntityCreature entity) {
        return entity != null && !entity.closed && entity.isAlive() && entity.canBeFollowed()
                && this.canTarget(entity) && this.targetOption(entity, this.distanceSquared(entity))
                && this.distanceSquared(entity) <= TARGET_RANGE_SQUARED;
    }

    private EntityCreature findSideHeadTarget(int side) {
        EntityCreature chosen = null;
        int count = 0;
        for (Entity entity : this.level.getEntitiesList()) {
            if (!(entity instanceof EntityCreature) || !isValidSideTarget((EntityCreature) entity)
                    || entity == this.followTarget || !this.seesTarget(entity)) continue;
            // Reservoir sampling avoids always selecting the nearest target and lets the side heads
            // independently choose from entities around the boss.
            if (Utils.rand(1, ++count) == 1) chosen = (EntityCreature) entity;
        }
        return chosen;
    }

    private void fireRandomBlueSkull(int head) {
        double angle = Utils.rand(0, 360);
        double radians = Math.toRadians(angle);
        Vector3 origin = this.add(Math.cos(radians) * 1.1, 2.2, Math.sin(radians) * 1.1);
        Entity projectile = Entity.createEntity("BlueWitherSkull", origin, this);
        if (!(projectile instanceof EntityBlueWitherSkull)) return;
        EntityBlueWitherSkull skull = (EntityBlueWitherSkull) projectile;
        skull.setExplode(true);
        skull.setMotion(new Vector3(Math.cos(radians) * 0.9, Utils.rand(-0.2, 0.2), Math.sin(radians) * 0.9));
        if (skull.level.hasCollisionBlocks(skull, skull.boundingBox)) {
            skull.close();
            return;
        }
        ProjectileLaunchEvent event = new ProjectileLaunchEvent(skull);
        this.server.getPluginManager().callEvent(event);
        if (event.isCancelled()) skull.close();
        else {
            skull.spawnToAll();
            this.level.addSound(this, Sound.MOB_WITHER_SHOOT);
        }
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (getServer().getDifficulty() == 0) {
            this.close();
            return true;
        }

        if (!this.closed && this.age == 220) {
            this.explode();
            this.setDataProperty(new IntEntityData(DATA_WITHER_INVULNERABLE_TICKS, 0));
        }

        boolean result = super.entityBaseTick(tickDiff);
        if (!this.closed && this.isAlive() && this.age > 220) {
            if (this.server.getTick() % 20 == 0 && this.getHealth() < this.getMaxHealth()) {
                this.setHealth(Math.min(this.getMaxHealth(), this.getHealth() + 1));
            }
            if (!this.halfHealthPhase && this.getHealth() <= this.getMaxHealth() / 2) {
                this.activateHalfHealthPhase();
            }
            this.updateSideHeads(tickDiff);
            EntityCreature main = this.followTarget instanceof EntityCreature ? (EntityCreature) this.followTarget
                    : this.target instanceof EntityCreature ? (EntityCreature) this.target : null;
            this.setDataProperty(new LongEntityData(DATA_WITHER_TARGET_1, main == null ? 0 : main.getId()));
            if (this.halfHealthPhase) {
                this.dashCooldown = Math.max(0, this.dashCooldown - tickDiff);
                if (this.dashTicks <= 0 && this.dashCooldown == 0 && main != null && isValidSideTarget(main)
                        && this.distanceSquared(main) >= 9 && this.distanceSquared(main) <= 900) {
                    this.dashTarget = main;
                    this.dashTicks = 5;
                    this.dashCooldown = 100;
                }
            }
        }
        return result;
    }

    private void activateHalfHealthPhase() {
        this.halfHealthPhase = true;
        this.witherArmor = true;
        this.namedTag.putBoolean("WitherHalfHealthPhase", true);
        this.namedTag.putBoolean("WitherArmor", true);
        EntityExplosionPrimeEvent event = new EntityExplosionPrimeEvent(this, 7.5f);
        this.server.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            Explosion explosion = new Explosion(this, (float) event.getForce(), this);
            if (event.isBlockBreaking() && this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING)) explosion.explodeA();
            explosion.explodeB();
        }
        if (this.server.getDifficulty() >= 2) {
            for (int i = 0; i < 4; i++) {
                double angle = Math.PI * 2 * i / 4;
                Vector3 spawn = this.add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
                Entity skeleton = Entity.createEntity("WitherSkeleton", spawn, this);
                if (skeleton != null && !skeleton.closed) skeleton.spawnToAll();
            }
        }
    }

    @Override
    public Vector3 updateMove(int tickDiff) {
        if (this.dashTicks > 0 && this.dashTarget != null && !this.dashTarget.closed && this.dashTarget.isAlive()) {
            Vector3 direction = new Vector3(this.dashTarget.x - this.x, this.dashTarget.y + this.dashTarget.getEyeHeight() - this.y, this.dashTarget.z - this.z).normalize();
            this.motionX = direction.x * 1.6;
            this.motionY = direction.y * 1.0;
            this.motionZ = direction.z * 1.6;
            this.breakDashBlocks();
            this.move(this.motionX, this.motionY, this.motionZ);
            this.updateMovement();
            this.dashTicks--;
            if (this.dashTicks <= 0) this.dashTarget = null;
            return this.dashTarget;
        }
        return super.updateMove(tickDiff);
    }

    private void breakDashBlocks() {
        if (!this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING)) return;
        int cx = this.getFloorX();
        int cy = this.getFloorY();
        int cz = this.getFloorZ();
        Item tool = Item.get(Item.DIAMOND_PICKAXE);
        Vector3 blockPos = new Vector3(0, 0, 0);
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int y = cy - 1; y <= cy + 4; y++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    Block block = this.level.getBlock(this.chunk, x, y, z, true);
                    if (block.isBreakable(tool)) {
                        this.level.setBlock(x, y, z, Block.LAYER_NORMAL, Block.get(Block.AIR), false, true);
                        BlockEntity blockEntity = this.level.getBlockEntityIfLoaded(this.chunk, blockPos.setComponents(x, y, z));
                        if (blockEntity != null) {
                            blockEntity.onBreak();
                            blockEntity.close();
                            this.level.updateComparatorOutputLevel(block);
                        }
                    }
                }
            }
        }
        for (Entity entity : this.level.getNearbyEntities(this.boundingBox.grow(1.2, 1.2, 1.2), this)) {
            if (!(entity instanceof EntityCreature) || !isValidSideTarget((EntityCreature) entity)) continue;
            entity.attack(new EntityDamageByEntityEvent(this, entity, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 12));
        }
    }

    @Override
    public Item[] getDrops() {
        return new Item[]{Item.get(Item.NETHER_STAR, 0, 1), Block.get(Item.WITHER_ROSE, 0).toItem()};
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
        addEntity.attributes = new Attribute[]{Attribute.getAttribute(Attribute.MAX_HEALTH).setMaxValue(witherMaxHealth()).setValue(witherMaxHealth())};
        return addEntity;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putBoolean("WitherHalfHealthPhase", this.halfHealthPhase);
        this.namedTag.putBoolean("WitherArmor", this.witherArmor);
    }

    @Override
    public void kill() {
        if (!this.isAlive()) return;
        if (!this.exploded && this.lastDamageCause != null && EntityDamageEvent.DamageCause.SUICIDE != this.lastDamageCause.getCause()) {
            if (this.lastDamageCause instanceof EntityDamageByEntityEvent) {
                Entity damager = ((EntityDamageByEntityEvent) this.lastDamageCause).getDamager();
                if (damager instanceof Player) ((Player) damager).awardAchievement("killWither");
            }
            this.exploded = true;
            this.explode();
        }
        super.kill();
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        if (this.age <= 220 && ev.getCause() != EntityDamageEvent.DamageCause.SUICIDE) return false;
        if (this.witherArmor && ev instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) ev).getDamager();
            if (ev instanceof EntityDamageByChildEntityEvent) damager = ((EntityDamageByChildEntityEvent) ev).getChild();
            if (damager instanceof EntityProjectile && !(damager instanceof EntityBlueWitherSkull)) return false;
        }

        boolean r = super.attack(ev);
        if (this.wasExplosion) {
            this.wasExplosion = false;
        } else if (r && server.explosionBreakBlocks && ev instanceof EntityDamageByEntityEvent && ((EntityDamageByEntityEvent) ev).getDamager() instanceof Player && !this.closed && this.isAlive() && Utils.rand() && this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING)) {
            this.wasExplosion = true;
            EntityExplosionPrimeEvent explosionPrimeEvent = new EntityExplosionPrimeEvent(this, 1);
            this.server.getPluginManager().callEvent(explosionPrimeEvent);
            if (!explosionPrimeEvent.isCancelled()) {
                this.level.addSound(this, Sound.MOB_WITHER_BREAK_BLOCK);
                if (!explosionPrimeEvent.isBlockBreaking()) return true;
                int fx = this.getFloorX();
                int fy = this.getFloorY();
                int fz = this.getFloorZ();
                Item tool = Item.get(Item.DIAMOND_PICKAXE);
                Vector3 pos = new Vector3(0, 0, 0);
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int x = fx - 2; x <= fx + 2; x++) {
                    for (int y = fy; y <= fy + 4; y++) {
                        for (int z = fz - 2; z <= fz + 2; z++) {
                            Block block = this.level.getBlock(this.chunk, x, y, z, true);
                            if (block.isBreakable(tool)) {
                                this.level.setBlock(x, y, z, Block.LAYER_NORMAL, Block.get(Block.AIR), false, true);
                                BlockEntity blockEntity = this.level.getBlockEntityIfLoaded(this.chunk, pos.setComponents(x, y, z));
                                if (blockEntity != null) {
                                    blockEntity.onBreak();
                                    blockEntity.close();
                                    this.level.updateComparatorOutputLevel(block);
                                }
                                if (this.level.getGameRules().getBoolean(GameRule.DO_TILE_DROPS) && random.nextDouble() * 100 < 14) {
                                    for (Item drop : block.getDrops(tool)) this.level.dropItem(block, drop);
                                }
                            }
                        }
                    }
                }
            }
        }
        return r;
    }

    private int witherMaxHealth() {
        switch (this.getServer().getDifficulty()) {
            case 2: return 450;
            case 3: return 600;
            default: return 300;
        }
    }

    private void explode() {
        EntityExplosionPrimeEvent ev = new EntityExplosionPrimeEvent(this, 7);
        this.server.getPluginManager().callEvent(ev);
        if (!ev.isCancelled()) {
            Explosion explosion = new Explosion(this, (float) ev.getForce(), this);
            if (ev.isBlockBreaking() && this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING)) explosion.explodeA();
            explosion.explodeB();
        }
    }

    @Override
    public boolean canDespawn() {
        return false;
    }
}

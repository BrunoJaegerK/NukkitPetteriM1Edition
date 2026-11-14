package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.data.ByteEntityData;
import cn.nukkit.entity.data.IntPositionEntityData;
import cn.nukkit.entity.data.ShortEntityData;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.block.Block;
import cn.nukkit.level.Location;
import cn.nukkit.event.player.PlayerTeleportEvent;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.entity.projectile.EntityShulkerBullet;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Utils;

public class EntityShulker extends EntityWalkingMob {

    public static final int NETWORK_ID = 54;

    private int peekTicks;
    private int attachmentCheckTicks;

    public EntityShulker(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
        this.route = null;
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 1f;
    }

    @Override
    public float getHeight() {
        return 1f;
    }

    @Override
    public double getSpeed() {
        return 0;
    }

    @Override
    protected void initEntity() {
        this.setMaxHealth(30);
        super.initEntity();
        this.fireProof = true;
        this.noFallDamage = true;

        if (this.namedTag.contains("Color")) {
            this.dataProperties.putInt(DATA_VARIANT, this.namedTag.getByte("Color"));
        } else {
            this.dataProperties.putInt(DATA_VARIANT, 16);
        }

        this.setCollidable(true);
        this.setDataProperty(new IntEntityData(DATA_SHULKER_PEEK_ID, 0));
        this.updateAttachmentMetadata();
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        boolean updated = super.entityBaseTick(tickDiff);
        if (this.peekTicks > 0) {
            this.peekTicks = Math.max(0, this.peekTicks - Math.max(1, tickDiff));
            if (this.peekTicks == 0) {
                this.setDataProperty(new IntEntityData(DATA_SHULKER_PEEK_ID, 0));
            }
        }
        this.attachmentCheckTicks += Math.max(0, tickDiff);
        if (this.attachmentCheckTicks >= 20) {
            this.attachmentCheckTicks %= 20;
            if (!this.updateAttachmentMetadata()) {
                this.teleportToSafePosition();
            }
        }
        return updated;
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (!(creature instanceof Player)) {
            return false;
        }
        Player player = (Player) creature;
        return this.getServer().getDifficulty() > 0 && !player.closed && player.spawned && player.isAlive()
                && (player.isSurvival() || player.isAdventure()) && distance <= 256;
    }

    /**
     * Publish the support block and face that anchor this Shulker to the world.
     * Bedrock clients use these metadata fields to render the shell orientation.
     */
    private boolean updateAttachmentMetadata() {
        int x = this.getFloorX();
        int y = this.getFloorY();
        int z = this.getFloorZ();
        int[][] supports = {
                {0, -1, 0, 1}, // block below: attach to its upper face
                {0, 1, 0, 0},  // block above: attach to its lower face
                {0, 0, -1, 3}, // north
                {0, 0, 1, 2},  // south
                {-1, 0, 0, 5}, // west
                {1, 0, 0, 4}   // east
        };

        for (int[] support : supports) {
            int sx = x + support[0];
            int sy = y + support[1];
            int sz = z + support[2];
            cn.nukkit.level.format.FullChunk chunk = this.level.getChunk(sx >> 4, sz >> 4, false);
            if (chunk == null || !chunk.isGenerated()) {
                continue;
            }
            if (Block.isBlockSolidById(chunk.getBlockId(sx & 0x0f, sy, sz & 0x0f))) {
                this.setDataProperty(new ByteEntityData(DATA_SHULKER_ATTACH_FACE, support[3]));
                this.setDataProperty(new ShortEntityData(DATA_SHULKER_ATTACHED, 1));
                this.setDataProperty(new IntPositionEntityData(DATA_SHULKER_ATTACH_POS, sx, sy, sz));
                return true;
            }
        }

        this.setDataProperty(new ShortEntityData(DATA_SHULKER_ATTACHED, 0));
        return false;
    }

    /**
     * Shulkers can relocate when damaged, but must not be moved into solid blocks
     * or unloaded chunks. Search only already-generated nearby chunks.
     */
    private boolean teleportToSafePosition() {
        for (int attempt = 0; attempt < 16; attempt++) {
            int tx = this.getFloorX() + Utils.rand(-8, 8);
            int ty = this.getFloorY() + Utils.rand(-4, 4);
            int tz = this.getFloorZ() + Utils.rand(-8, 8);
            if (ty <= this.level.getMinBlockY() || ty + 1 > this.level.getMaxBlockY()) {
                continue;
            }

            cn.nukkit.level.format.FullChunk candidate = this.level.getChunk(tx >> 4, tz >> 4, false);
            if (candidate == null || !candidate.isGenerated()) {
                continue;
            }

            int localX = tx & 0x0f;
            int localZ = tz & 0x0f;
            int support = candidate.getBlockId(localX, ty - 1, localZ);
            int feet = candidate.getBlockId(localX, ty, localZ);
            int head = candidate.getBlockId(localX, ty + 1, localZ);
            if (!Block.isBlockSolidById(support) || feet != Block.AIR || head != Block.AIR) {
                continue;
            }

            Location destination = new Location(tx + 0.5, ty, tz + 0.5, this.yaw, this.pitch, this.level);
            if (this.teleport(destination, PlayerTeleportEvent.TeleportCause.UNKNOWN)) {
                this.updateAttachmentMetadata();
                return true;
            }
        }
        return false;
    }

    @Override
    public void attackEntity(Entity player) {
        if (player == null || player.closed || !player.isAlive()) {
            return;
        }
        if (this.attackDelay > 60 && Utils.rand(1, 32) < 4 && this.distanceSquared(player) <= 256) {
            this.attackDelay = 0;

            EntityShulkerBullet shot = (EntityShulkerBullet) Entity.createEntity("ShulkerBullet", this.add(0, this.getEyeHeight(), 0), this);
            if (shot == null) {
                return;
            }

            if (shot.level.hasCollisionBlocks(shot, shot.boundingBox)) {
                shot.close();
                return;
            }

            shot.target = player;
            shot.setMotion(player.subtract(this).normalize().multiply(0.5));

            ProjectileLaunchEvent launch = new ProjectileLaunchEvent(shot);
            this.server.getPluginManager().callEvent(launch);

            if (launch.isCancelled()) {
                shot.close();
            } else {
                shot.spawnToAll();
                this.peekTicks = 20;
                this.setDataProperty(new IntEntityData(DATA_SHULKER_PEEK_ID, 100));
                this.level.addSound(this, Sound.MOB_SHULKER_SHOOT);
            }
        }
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        // A closed shell deflects projectiles and grants the Shulker its 20 armor points.
        if (this.peekTicks <= 0 && ev.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
            ev.setCancelled(true);
        } else if (this.peekTicks <= 0 && ev.canBeReducedByArmor()) {
            float baseDamage = ev.getDamage(EntityDamageEvent.DamageModifier.BASE);
            float effectiveArmor = Math.min(20f, Math.max(4f, 20f - baseDamage / 2f));
            ev.setDamage(EntityDamageEvent.DamageModifier.ARMOR, -(baseDamage * effectiveArmor / 25f));
        }

        boolean damaged = super.attack(ev);
        if (damaged && !ev.isCancelled() && this.getHealth() < this.getMaxHealth() / 2f) {
            if (Utils.rand(1, 4) == 1 && this.teleportToSafePosition()) {
                this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_TELEPORT);
            }
        }
        return damaged;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        // Bedrock Shulkers remain in Peaceful, but become passive.
        if (this.getServer().getDifficulty() != 0) {
            return super.onUpdate(currentTick);
        }
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
        this.motionX = this.motionY = this.motionZ = 0;
        this.target = null;
        this.followTarget = null;
        return true;
    }

    @Override
    public Item[] getDrops() {
        return new Item[]{Item.get(Item.SHULKER_SHELL, 0, Utils.rand(0, 1))};
    }

    @Override
    public int getKillExperience() {
        return 5;
    }

    @Override
    public void knockBack(Entity attacker, double damage, double x, double z, double base) {
    }

    @Override
    public boolean canDespawn() {
        return false;
    }
}

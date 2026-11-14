package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.mob.EntityEndermite;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.event.entity.EndermanBlockPickUpEvent;
import cn.nukkit.event.entity.EndermanBlockPlaceEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerTeleportEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.biome.Biome;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.Utils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.HashMap;

public class EntityEnderman extends EntityWalkingMob {

    public static final int NETWORK_ID = 38;

    private int angry = 0;

    private boolean teleported;

    private boolean pickupBlocks;

    private Item block;

    private static final IntOpenHashSet canPickup = new IntOpenHashSet(new int[]{
            BlockID.DIRT, BlockID.GRASS, BlockID.PODZOL, BlockID.MYCELIUM,
            BlockID.SAND, BlockID.GRAVEL, BlockID.TALL_GRASS, BlockID.DEAD_BUSH,
            BlockID.DANDELION, BlockID.FLOWER, BlockID.BROWN_MUSHROOM, BlockID.RED_MUSHROOM,
            BlockID.TNT, BlockID.CACTUS, BlockID.CLAY_BLOCK, BlockID.PUMPKIN,
            BlockID.LIT_PUMPKIN, BlockID.BROWN_MUSHROOM_BLOCK, BlockID.RED_MUSHROOM_BLOCK,
            BlockID.MELON_BLOCK, BlockID.CARVED_PUMPKIN, BlockID.ROOTED_DIRT, BlockID.MOSS_BLOCK,
            BlockID.MUD, BlockID.MUDDY_MANGROVE_ROOTS, BlockID.CRIMSON_ROOTS, BlockID.WARPED_ROOTS,
            BlockID.CRIMSON_FUNGUS, BlockID.WARPED_FUNGUS, BlockID.CRIMSON_NYLIUM, BlockID.WARPED_NYLIUM
    });

    public EntityEnderman(FullChunk chunk, CompoundTag nbt) {
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
        return 2.9f;
    }

    @Override
    public double getSpeed() {
        return this.isAngry() ? 1.6 : 1.21;
    }

    @Override
    protected void initEntity() {
        this.setMaxHealth(40);
        super.initEntity();

        this.setDamage(new int[]{0, 4, 7, 10});

        if (this.namedTag.contains("Block")) {
            this.block = NBTIO.getItemHelper(this.namedTag.getCompound("Block"));
            this.setDataProperty(new IntEntityData(DATA_ENDERMAN_HELD_RUNTIME_ID, GlobalBlockPalette.getOrCreateRuntimeId(ProtocolInfo.CURRENT_PROTOCOL, this.block.getId(), this.block.getDamage())));
        }

        this.pickupBlocks = !server.suomiCraftPEMode() && this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING);
        if (this.namedTag.contains("AngerTicks")) {
            this.setAngry(Math.max(0, this.namedTag.getInt("AngerTicks")));
        }
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

    @Override
    public boolean attack(EntityDamageEvent ev) {
        if (ev.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
            setAngry(2400);
            ev.setCancelled(true);
            this.teleport();
            return false;
        }

        boolean damaged = super.attack(ev);
        if (damaged && !ev.isCancelled()) {
            if (ev.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                setAngry(2400);
            }

            if (!this.teleported && Utils.rand(1, 10) == 1) {
                this.teleport();
            }
        }
        return damaged;
    }

    @Override
    public Item[] getDrops() {
        if (this.block != null) {
            return new Item[]{Item.get(Item.ENDER_PEARL, 0, Utils.rand(0, 1)), block};
        }
        return new Item[]{Item.get(Item.ENDER_PEARL, 0, Utils.rand(0, 1))};
    }

    @Override
    public int getKillExperience() {
        return 5;
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (this.closed) {
            return false;
        }

        if (this.getServer().getDifficulty() == 0) {
            this.close();
            return false;
        }

        this.teleported = false;
        this.pickupBlocks = !server.suomiCraftPEMode()
                && this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING);

        if (this.angry > 0) {
            this.setAngry(Math.max(0, this.angry - Math.max(1, tickDiff)));
        }

        int b = level.getBlockIdAt(chunk, this.getFloorX(), this.getFloorY(), this.getFloorZ());
        if (this.isOnFire()) {
            this.teleport();
        } else if (Block.isWater(b) || (this.level.isRaining() && Utils.rand() && this.canSeeSky() && Biome.getBiome(level.getBiomeId((int) x, (int) z)).canRain())) {
            this.attack(new EntityDamageEvent(this, EntityDamageEvent.DamageCause.DROWNING, 1));
            this.setAngry(0);
            this.teleport();
        } else if (Utils.rand(0, 500) == 20) {
            this.setAngry(0);
            this.teleport();
        } else if (this.pickupBlocks) {
            if (this.block == null && Utils.rand(0, 19) == 0) {
                this.tryPickupNearbyBlock();
            } else if (this.block != null && Utils.rand(0, 1999) == 0) {
                this.tryPlaceHeldBlock();
            }
        }

        return super.entityBaseTick(tickDiff);
    }

    private void tryPickupNearbyBlock() {
        int x = this.getFloorX() + Utils.rand(-2, 1);
        int y = this.getFloorY() + Utils.rand(-1, 1);
        int z = this.getFloorZ() + Utils.rand(-2, 1);
        FullChunk candidateChunk = this.level.getChunkIfLoaded(x >> 4, z >> 4);
        if (candidateChunk == null || !candidateChunk.isGenerated()) {
            return;
        }

        Block candidate = this.level.getBlock(candidateChunk, x, y, z, false);
        if (!canPickup.contains(candidate.getId()) || !this.hasLineOfSightToBlock(candidate)) {
            return;
        }
        this.pickupBlock(candidate);
    }

    private boolean hasLineOfSightToBlock(Block target) {
        Vector3 start = this.add(0, this.getEyeHeight(), 0);
        Vector3 end = new Vector3(target.x + 0.5, target.y + 0.5, target.z + 0.5);
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(distance * 4));

        for (int step = 1; step <= steps; step++) {
            double progress = step / (double) steps;
            int x = (int) Math.floor(start.x + dx * progress);
            int y = (int) Math.floor(start.y + dy * progress);
            int z = (int) Math.floor(start.z + dz * progress);
            if (x == target.getFloorX() && y == target.getFloorY() && z == target.getFloorZ()) {
                return true;
            }

            FullChunk chunk = this.level.getChunkIfLoaded(x >> 4, z >> 4);
            if (chunk == null || !chunk.isGenerated() || chunk.getBlockId(x & 0x0f, y, z & 0x0f) != Block.AIR) {
                return false;
            }
        }
        return true;
    }

    private void tryPlaceHeldBlock() {
        if (this.block == null) {
            return;
        }

        int x = this.getFloorX() + Utils.rand(-1, 0);
        int y = this.getFloorY() + Utils.rand(-1, 0);
        int z = this.getFloorZ() + Utils.rand(-1, 0);
        if (y <= this.level.getMinBlockY() || y > this.level.getMaxBlockY()) {
            return;
        }

        FullChunk chunk = this.level.getChunkIfLoaded(x >> 4, z >> 4);
        if (chunk == null || !chunk.isGenerated()) {
            return;
        }

        int localX = x & 0x0f;
        int localZ = z & 0x0f;
        if (chunk.getBlockId(localX, y, localZ) != Block.AIR) {
            return;
        }

        int supportId = chunk.getBlockId(localX, y - 1, localZ);
        if (!Block.isBlockSolidById(supportId) || supportId == BlockID.BEDROCK) {
            return;
        }

        Block placedBlock = Block.get(this.block.getId(), this.block.getDamage(),
                new Location(x, y, z, this.level));
        if (!placedBlock.canBePlaced()) {
            return;
        }

        AxisAlignedBB box = placedBlock.getBoundingBox();
        if (box != null) {
            for (Entity entity : this.level.getNearbyEntities(box, this)) {
                if (entity.isAlive() && entity.getBoundingBox().intersectsWith(box)) {
                    return;
                }
            }
        }

        EndermanBlockPlaceEvent event = new EndermanBlockPlaceEvent(this, placedBlock);
        this.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        // Revalidate after plugins have had a chance to inspect the placement.
        if (this.level.getBlockIdAt(chunk, x, y, z) != Block.AIR
                || !Block.isBlockSolidById(this.level.getBlockIdAt(chunk, x, y - 1, z))
                || !this.level.setBlock(placedBlock, placedBlock, true, true)) {
            return;
        }

        this.block = null;
        this.setDataProperty(new IntEntityData(DATA_ENDERMAN_HELD_RUNTIME_ID, 0));
    }

    private void pickupBlock(Block block) {
        if (!block.isValid()) {
            return;
        }

        EndermanBlockPickUpEvent event = new EndermanBlockPickUpEvent(this, block);
        this.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        level.setBlock(block, Block.get(Block.AIR));
        this.block = block.toItem();
        this.setDataProperty(new IntEntityData(DATA_ENDERMAN_HELD_RUNTIME_ID, GlobalBlockPalette.getOrCreateRuntimeId(ProtocolInfo.CURRENT_PROTOCOL, block.getId(), block.getDamage())));

    }

    /**
     * Teleport enderman to random location in 32x32 range
     */
    public void teleport() {
        Location to = this.getSafeTpLocation();
        if (to != null) {
            this.level.addLevelEvent(this, LevelEventPacket.EVENT_SOUND_ENDERMAN_TELEPORT);
            if (this.teleport(to, PlayerTeleportEvent.TeleportCause.UNKNOWN)) {
                this.level.addLevelEvent(this, LevelEventPacket.EVENT_SOUND_ENDERMAN_TELEPORT);
                this.teleported = true;
            }
        }
    }

    private Location getSafeTpLocation() {
        for (int attempt = 0; attempt < 16; attempt++) {
            double dx = this.x + Utils.rand(-16, 16);
            double dz = this.z + Utils.rand(-16, 16);
            Vector3 pos = new Vector3(Math.floor(dx), (int) Math.floor(this.y + 0.1) + 16, Math.floor(dz));
            FullChunk chunk = this.level.getChunk((int) pos.x >> 4, (int) pos.z >> 4, false);
            if (chunk == null || !chunk.isGenerated()) {
                continue;
            }

            int x = (int) pos.x & 0x0f;
            int z = (int) pos.z & 0x0f;
            int previousY1 = -1000;
            int previousY2 = -1000;
            int previousY3 = -1000;
            for (int y = Math.min(this.level.getMaxBlockY(), (int) pos.y); y >= this.level.getMinBlockY(); y--) {
                if (previousY1 > -1000 && previousY2 > -1000 && previousY3 > -1000
                        && Block.isBlockSolidById(chunk.getBlockId(x, y, z))
                        && chunk.getBlockId(x, previousY1, z) == Block.AIR
                        && chunk.getBlockId(x, previousY2, z) == Block.AIR
                        && chunk.getBlockId(x, previousY3, z) == Block.AIR) {
                    return new Location(pos.x + 0.5, previousY1 + 0.1, pos.z + 0.5, this.level);
                }
                previousY3 = previousY2;
                previousY2 = previousY1;
                previousY1 = y;
            }
        }
        return null;
    }

    @Override
    public boolean canDespawn() {
        if (this.getLevel().getDimension() == Level.DIMENSION_THE_END) {
            return false;
        }

        return (this.block == null || !this.pickupBlocks) && super.canDespawn();
    }

    public boolean isAngry() {
        return this.angry > 0;
    }

    public void setAngry(int val) {
        if (this.angry != val) {
            this.angry = val;
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_ANGRY, val > 0);
        }
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (creature instanceof EntityEndermite) {
            return !creature.closed && creature.isAlive() && distance <= 4096;
        }
        if (!isAngry() || !(creature instanceof Player)) {
            return false;
        }
        Player player = (Player) creature;
        return !player.closed && player.spawned && player.isAlive()
                && (player.isSurvival() || player.isAdventure()) && distance <= 4096; // 64 blocks
    }

    public void stareToAngry() {
        setAngry(2400);
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        this.saveBlock();
        this.namedTag.putInt("AngerTicks", this.angry);
    }

    private void saveBlock() {
        if (block != null) {
            this.namedTag.put("Block", NBTIO.putItemHelper(block));
        }
    }

    @Override
    public boolean ignoredAsSaveReason() {
        return !this.pickupBlocks && super.ignoredAsSaveReason();
    }
}

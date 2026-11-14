package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockCrops;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.mob.EntityWitch;
import cn.nukkit.entity.mob.EntityDrowned;
import cn.nukkit.entity.mob.EntityEvoker;
import cn.nukkit.entity.mob.EntityHusk;
import cn.nukkit.entity.mob.EntityPillager;
import cn.nukkit.entity.mob.EntityRavager;
import cn.nukkit.entity.mob.EntityVex;
import cn.nukkit.entity.mob.EntityVindicator;
import cn.nukkit.entity.mob.EntityZombie;
import cn.nukkit.entity.mob.EntityZombieVillager;
import cn.nukkit.event.entity.CreatureSpawnEvent;
import cn.nukkit.event.entity.VillagerHarvestEvent;
import cn.nukkit.event.entity.VillagerPlantEvent;
import cn.nukkit.entity.route.WalkerRouteFinder;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.inventory.TradeInventory;
import cn.nukkit.inventory.TradeInventoryRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.Location;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;

import java.util.ArrayList;
import java.util.List;

public class EntityVillagerV1 extends EntityWalkingAnimal implements InventoryHolder {

    public static final int PROFESSION_FARMER = 0;
    public static final int PROFESSION_LIBRARIAN = 1;
    public static final int PROFESSION_PRIEST = 2;
    public static final int PROFESSION_BLACKSMITH = 3;
    public static final int PROFESSION_BUTCHER = 4;
    public static final int PROFESSION_GENERIC = 5;

    public static final int NETWORK_ID = 15;

    private TradeInventory inventory;
    private final List<TradeInventoryRecipe> recipes = new ArrayList<>();
    private int tradeTier = 0;
    private boolean willing = true;
    private int foodPoints;
    private int foodPickupTicks;
    private int routineSearchTicks;
    private Vector3 routineTarget;
    private Vector3 farmTarget;
    private boolean farmPlantingTarget;
    private int farmSearchTicks;
    private int wheatSeeds;
    private int carrotSeeds;
    private int potatoSeeds;
    private int beetrootSeeds;

    public EntityVillagerV1(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
        this.route = new WalkerRouteFinder(this);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        if (this.isBaby()) {
            return 0.3f;
        }
        return 0.6f;
    }

    @Override
    public float getHeight() {
        if (this.isBaby()) {
            return 0.975f;
        }
        return 1.9f;
    }

    @Override
    public double getSpeed() {
        return 1.1;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(20);
        super.initEntity();

        this.inventory = new TradeInventory(this);
        this.foodPoints = Math.min(12, Math.max(0, this.namedTag.getInt("FoodPoints")));
        this.wheatSeeds = Math.max(0, this.namedTag.getInt("WheatSeeds"));
        this.carrotSeeds = Math.max(0, this.namedTag.getInt("CarrotSeeds"));
        this.potatoSeeds = Math.max(0, this.namedTag.getInt("PotatoSeeds"));
        this.beetrootSeeds = Math.max(0, this.namedTag.getInt("BeetrootSeeds"));

        try {
            CompoundTag offers = this.namedTag.getCompound("Offers");
            if (offers != null) {
                ListTag<CompoundTag> nbtRecipes = offers.getList("Recipes", CompoundTag.class);
                for (CompoundTag nbt : nbtRecipes.getAll()) {
                    recipes.add(TradeInventoryRecipe.toNBT(nbt));
                }
            }
        } catch (Exception ex) {
            server.getLogger().error("Failed to load trade recipes for a villager with entity id " + this.id, ex);
        }

        if (!this.namedTag.contains("Profession")) {
            this.setProfession(PROFESSION_GENERIC);
        }
    }

    public int getProfession() {
        return this.namedTag.getInt("Profession");
    }

    public void setProfession(int profession) {
        this.namedTag.putInt("Profession", profession);
        this.routineSearchTicks = 0;
        this.routineTarget = null;
        this.farmSearchTicks = 0;
        this.farmTarget = null;
        this.farmPlantingTarget = false;
    }

    @Override
    public boolean canTarget(Entity entity) {
        if (this.isBaby() && entity instanceof EntityVillagerV1 && !((EntityVillagerV1) entity).isBaby()) {
            return entity.isAlive() && !entity.closed && entity.canBeFollowed();
        }
        return super.canTarget(entity);
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (this.isBaby() && creature instanceof EntityVillagerV1 && !((EntityVillagerV1) creature).isBaby()) {
            return distance <= 64;
        }
        return super.targetOption(creature, distance);
    }

    /**
     * Villagers flee nearby hostile mobs instead of continuing their normal wandering route.
     * Kept local to villagers so the shared movement AI used by other entities is untouched.
     */
    @Override
    protected void checkTarget() {
        if (this.isKnockback() || this.isImmobile()) {
            super.checkTarget();
            return;
        }

        Entity threat = null;
        double nearestDistance = 64.0; // 8 blocks squared
        for (Entity entity : this.level.getNearbyEntities(this.boundingBox.grow(8, 6, 8), this)) {
            if (entity == this || entity.closed || !entity.isAlive() || !this.isVillagerThreat(entity)) {
                continue;
            }
            double distance = this.distanceSquared(entity);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                threat = entity;
            }
        }

        if (threat != null) {
            double dx = this.x - threat.x;
            double dz = this.z - threat.z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 0.001) {
                dx = (this.getId() & 1) == 0 ? 1 : -1;
                dz = 0;
                length = 1;
            }
            this.followTarget = null;
            this.target = new Vector3(this.x + (dx / length) * 12,
                    this.y, this.z + (dz / length) * 12);
            this.stayTime = 0;
            this.moveTime = 30;
            this.doPanic(true);
            this.moveMultiplier = 1.2f;
            return;
        }

        if (!this.isBaby() && this.getProfession() == PROFESSION_FARMER
                && this.isFarmerWorkTime() && this.farmTarget != null) {
            if (this.distanceSquared(this.farmTarget) <= 2.25) {
                if (this.farmPlantingTarget) {
                    this.plantFarmTarget();
                } else {
                    this.harvestFarmTarget();
                }
                return;
            }
            this.followTarget = null;
            this.target = this.farmTarget;
            this.stayTime = 0;
            this.moveTime = 40;
            return;
        }

        if (!this.isBaby()) {
            if (this.routineSearchTicks <= 0) {
                this.updateRoutineTarget();
            }
            if (this.routineTarget != null) {
                this.followTarget = null;
                this.target = this.routineTarget;
                this.stayTime = 0;
                return;
            }
        }

        super.checkTarget();
    }

    private boolean isVillagerThreat(Entity entity) {
        return entity instanceof EntityZombie || entity instanceof EntityZombieVillager
                || entity instanceof EntityHusk || entity instanceof EntityDrowned
                || entity instanceof EntityPillager || entity instanceof EntityVindicator
                || entity instanceof EntityEvoker || entity instanceof EntityRavager
                || entity instanceof EntityVex;
    }

    /**
     * Lightweight Bedrock-style daily routine. Work, meeting and rest targets are
     * searched only every 10 seconds and only in already-loaded chunks.
     */
    private void updateRoutineTarget() {
        this.routineSearchTicks = 200;
        long time = Math.floorMod((long) this.level.getTime(), 24000L);
        int[] wantedBlocks = null;

        if (this.level.isRaining()) {
            wantedBlocks = new int[]{BlockID.BED_BLOCK};
        } else if (time >= 2000 && time < 9000) {
            switch (this.getProfession()) {
                case PROFESSION_FARMER:
                    wantedBlocks = new int[]{BlockID.COMPOSTER};
                    break;
                case PROFESSION_LIBRARIAN:
                    wantedBlocks = new int[]{BlockID.LECTERN};
                    break;
                case PROFESSION_PRIEST:
                    wantedBlocks = new int[]{BlockID.BREWING_STAND_BLOCK};
                    break;
                case PROFESSION_BLACKSMITH:
                    wantedBlocks = new int[]{BlockID.BLAST_FURNACE, BlockID.LIT_BLAST_FURNACE,
                            BlockID.SMITHING_TABLE, BlockID.GRINDSTONE};
                    break;
                case PROFESSION_BUTCHER:
                    wantedBlocks = new int[]{BlockID.SMOKER, BlockID.LIT_SMOKER};
                    break;
                default:
                    break;
            }
        } else if (time >= 9000 && time < 11000) {
            wantedBlocks = new int[]{461}; // Bell
        } else if (time >= 13000 || time < 1000) {
            wantedBlocks = new int[]{BlockID.BED_BLOCK};
        }

        this.routineTarget = wantedBlocks == null ? null : this.findNearbyRoutineBlock(wantedBlocks);
    }

    private Vector3 findNearbyRoutineBlock(int[] wantedBlocks) {
        int centerX = this.getFloorX();
        int centerY = this.getFloorY();
        int centerZ = this.getFloorZ();
        int radius = 12;
        double nearestDistance = Double.MAX_VALUE;
        Vector3 nearest = null;
        Entity[] nearbyEntities = this.level.getNearbyEntities(this.boundingBox.grow(16, 8, 16), this);

        for (int z = centerZ - radius; z <= centerZ + radius; z++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                cn.nukkit.level.format.FullChunk candidate = this.level.getChunkIfLoaded(x >> 4, z >> 4);
                if (candidate == null || !candidate.isGenerated()) {
                    continue;
                }

                int localX = x & 0x0f;
                int localZ = z & 0x0f;
                int minY = Math.max(this.level.getMinBlockY(), centerY - 4);
                int maxY = Math.min(this.level.getMaxBlockY(), centerY + 4);
                for (int y = minY; y <= maxY; y++) {
                    int blockId = candidate.getBlockId(localX, y, localZ);
                    if (!containsBlock(wantedBlocks, blockId)) {
                        continue;
                    }

                    Vector3 standPosition = this.findRoutineStandPosition(x, y, z);
                    if (standPosition == null || this.isRoutineBlockReserved(nearbyEntities,
                            standPosition.x, standPosition.y, standPosition.z, blockId)) {
                        continue;
                    }

                    double dx = standPosition.x - this.x;
                    double dy = standPosition.y - this.y;
                    double dz = standPosition.z - this.z;
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = standPosition;
                    }
                }
            }
        }
        return nearest;
    }

    private Vector3 findRoutineStandPosition(int poiX, int poiY, int poiZ) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0, 0}};
        for (int[] offset : offsets) {
            int x = poiX + offset[0];
            int z = poiZ + offset[1];
            cn.nukkit.level.format.FullChunk chunk = this.level.getChunkIfLoaded(x >> 4, z >> 4);
            if (chunk == null || !chunk.isGenerated()) {
                continue;
            }

            int localX = x & 0x0f;
            int localZ = z & 0x0f;
            int support = chunk.getBlockId(localX, poiY - 1, localZ);
            int feet = chunk.getBlockId(localX, poiY, localZ);
            int head = chunk.getBlockId(localX, poiY + 1, localZ);
            if (Block.isBlockSolidById(support) && feet == Block.AIR && head == Block.AIR) {
                return new Vector3(x + 0.5, poiY, z + 0.5);
            }
        }
        return null;
    }

    private boolean isRoutineBlockReserved(Entity[] nearbyEntities, double x, double y, double z, int blockId) {
        // Bells are meeting points and intentionally allow multiple villagers.
        if (blockId == 461) {
            return false;
        }

        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof EntityVillagerV1) || entity.closed) {
                continue;
            }
            Vector3 reserved = ((EntityVillagerV1) entity).routineTarget;
            if (reserved == null) {
                continue;
            }
            double dx = reserved.x - x;
            double dy = reserved.y - y;
            double dz = reserved.z - z;
            if (dx * dx + dy * dy + dz * dz <= 1.1) {
                return true;
            }
        }
        return false;
    }

    private boolean containsBlock(int[] blockIds, int blockId) {
        for (int candidate : blockIds) {
            if (candidate == blockId) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getKillExperience() {
        return 0;
    }

    @Override
    public void onStruckByLightning(Entity lightning) {
        Entity ent = Entity.createEntity("Witch", this);
        if (ent != null) {
            CreatureSpawnEvent cse = new CreatureSpawnEvent(EntityWitch.NETWORK_ID, this, ent.namedTag, CreatureSpawnEvent.SpawnReason.LIGHTNING);
            this.getServer().getPluginManager().callEvent(cse);

            if (cse.isCancelled()) {
                ent.close();
                return;
            }

            ent.yaw = this.yaw;
            ent.pitch = this.pitch;
            ent.setImmobile(this.isImmobile());
            if (this.hasCustomName()) {
                ent.setNameTag(this.getNameTag());
                ent.setNameTagVisible(this.isNameTagVisible());
                ent.setNameTagAlwaysVisible(this.isNameTagAlwaysVisible());
            }
            /*if (this.isBaby()) {
                ent.setBaby(); // TODO
            }*/

            this.close();
            ent.spawnToAll();
        } else {
            super.onStruckByLightning(lightning);
        }
    }

    public void setTradeTier(int tier) {
        this.tradeTier = tier;
    }

    public int getTradeTier() {
        return this.tradeTier;
    }

    public void setWilling(boolean value) {
        this.willing = value;
    }

    public boolean isWilling() {
        return this.willing;
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (!recipes.isEmpty()) {
            player.addWindow(this.getInventory());
            return true;
        }
        return false;
    }

    private int getBreedingFoodValue(Item item) {
        switch (item.getId()) {
            case Item.BREAD:
                return 4;
            case Item.CARROT:
            case Item.POTATO:
            case Item.BEETROOT:
                return 1;
            default:
                return 0;
        }
    }

    public void addTradeRecipe(TradeInventoryRecipe recipe) {
        this.recipes.add(recipe);
    }

    public List<TradeInventoryRecipe> getRecipes() {
        return this.recipes;
    }

    public CompoundTag getOffers() {
        CompoundTag nbt = new CompoundTag();
        nbt.putList(recipesToNbt());
        nbt.putList(getDefaultTierExpRequirements());
        return nbt;
    }

    private ListTag<CompoundTag> recipesToNbt() {
        ListTag<CompoundTag> tag = new ListTag<>("Recipes");
        for (TradeInventoryRecipe recipe : this.recipes) {
            tag.add(recipe.toNBT());
        }
        return tag;
    }

    private ListTag<CompoundTag> getDefaultTierExpRequirements() {
        ListTag<CompoundTag> tag = new ListTag<>("TierExpRequirements");
        tag.add(new CompoundTag().putInt("0", 0));
        tag.add(new CompoundTag().putInt("1", 10));
        tag.add(new CompoundTag().putInt("2", 70));
        tag.add(new CompoundTag().putInt("3", 150));
        tag.add(new CompoundTag().putInt("4", 250));
        return tag;
    }

    @Override
    public TradeInventory getInventory() {
        return this.inventory;
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        boolean updated = super.entityBaseTick(tickDiff);
        if (this.foodPoints >= 12 && !this.isBaby() && !this.isInLove() && !this.isInLoveCooldown()) {
            this.foodPoints = 0;
            this.setInLove();
            this.namedTag.putInt("FoodPoints", this.foodPoints);
        }
        if (this.routineSearchTicks > 0) {
            this.routineSearchTicks = Math.max(0, this.routineSearchTicks - Math.max(0, tickDiff));
        }
        if (this.farmSearchTicks > 0) {
            this.farmSearchTicks = Math.max(0, this.farmSearchTicks - Math.max(0, tickDiff));
        }
        boolean canFarm = this.getServer().getMobAiEnabled()
                && this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING)
                && this.isAlive() && !this.isBaby() && this.getProfession() == PROFESSION_FARMER
                && this.isFarmerWorkTime();
        if (canFarm && this.farmSearchTicks <= 0) {
            this.farmTarget = this.findNearestMatureCrop();
            this.farmPlantingTarget = false;
            if (this.farmTarget == null) {
                this.farmTarget = this.findNearestPlantableFarmland();
                this.farmPlantingTarget = this.farmTarget != null;
            }
            this.farmSearchTicks = 20;
        } else if (!canFarm) {
            this.farmTarget = null;
            this.farmPlantingTarget = false;
            this.farmSearchTicks = 0;
        }
        if (this.getServer().getMobAiEnabled() && this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING)
                && this.isAlive() && !this.isBaby() && !this.isInLove() && !this.isInLoveCooldown()) {
            this.foodPickupTicks += Math.max(0, tickDiff);
            if (this.foodPickupTicks >= 20) {
                this.foodPickupTicks %= 20;
                this.pickupBreedingFood();
            }
            if (this.foodPoints >= 8 && this.age % 100 == 0) {
                this.shareFoodWithNearbyVillager();
            }
        }
        return updated;
    }

    private boolean isFarmerWorkTime() {
        long time = this.level.getTime() % 24000;
        return !this.level.isRaining() && time >= 2000 && time < 9000;
    }

    private Vector3 findNearestMatureCrop() {
        int centerX = this.getFloorX();
        int centerY = this.getFloorY();
        int centerZ = this.getFloorZ();
        double nearestDistance = 64.0;
        Vector3 nearest = null;

        for (int z = centerZ - 8; z <= centerZ + 8; z++) {
            for (int x = centerX - 8; x <= centerX + 8; x++) {
                FullChunk chunk = this.level.getChunkIfLoaded(x >> 4, z >> 4);
                if (chunk == null || !chunk.isGenerated()) {
                    continue;
                }
                for (int y = centerY - 1; y <= centerY + 2; y++) {
                    Block block = this.level.getBlock(chunk, x, y, z, false);
                    if (!(block instanceof BlockCrops) || block.getDamage() < 7) {
                        continue;
                    }
                    double dx = x + 0.5 - this.x;
                    double dy = y - this.y;
                    double dz = z + 0.5 - this.z;
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = new Vector3(x + 0.5, y, z + 0.5);
                    }
                }
            }
        }
        return nearest;
    }

    private Vector3 findNearestPlantableFarmland() {
        if (this.wheatSeeds + this.carrotSeeds + this.potatoSeeds + this.beetrootSeeds <= 0) {
            return null;
        }
        int centerX = this.getFloorX();
        int centerY = this.getFloorY();
        int centerZ = this.getFloorZ();
        double nearestDistance = 64.0;
        Vector3 nearest = null;

        for (int z = centerZ - 8; z <= centerZ + 8; z++) {
            for (int x = centerX - 8; x <= centerX + 8; x++) {
                FullChunk chunk = this.level.getChunkIfLoaded(x >> 4, z >> 4);
                if (chunk == null || !chunk.isGenerated()) {
                    continue;
                }
                int localX = x & 0x0f;
                int localZ = z & 0x0f;
                for (int y = centerY - 1; y <= centerY + 2; y++) {
                    if (chunk.getBlockId(localX, y - 1, localZ) != BlockID.FARMLAND
                            || chunk.getBlockId(localX, y, localZ) != Block.AIR
                            || chunk.getBlockId(localX, y + 1, localZ) != Block.AIR) {
                        continue;
                    }
                    double dx = x + 0.5 - this.x;
                    double dy = y - this.y;
                    double dz = z + 0.5 - this.z;
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = new Vector3(x + 0.5, y, z + 0.5);
                    }
                }
            }
        }
        return nearest;
    }

    private void plantFarmTarget() {
        if (this.farmTarget == null) {
            return;
        }
        int x = this.farmTarget.getFloorX();
        int y = this.farmTarget.getFloorY();
        int z = this.farmTarget.getFloorZ();
        FullChunk chunk = this.level.getChunkIfLoaded(x >> 4, z >> 4);
        if (chunk == null || !chunk.isGenerated()
                || chunk.getBlockId(x & 0x0f, y - 1, z & 0x0f) != BlockID.FARMLAND
                || chunk.getBlockId(x & 0x0f, y, z & 0x0f) != Block.AIR
                || chunk.getBlockId(x & 0x0f, y + 1, z & 0x0f) != Block.AIR) {
            this.farmTarget = null;
            this.farmPlantingTarget = false;
            return;
        }

        int cropId;
        if (this.wheatSeeds > 0) {
            cropId = BlockID.WHEAT_BLOCK;
        } else if (this.carrotSeeds > 0) {
            cropId = BlockID.CARROT_BLOCK;
        } else if (this.potatoSeeds > 0) {
            cropId = BlockID.POTATO_BLOCK;
        } else if (this.beetrootSeeds > 0) {
            cropId = BlockID.BEETROOT_BLOCK;
        } else {
            this.farmTarget = null;
            this.farmPlantingTarget = false;
            return;
        }

        Location position = new Location(x, y, z, this.level);
        Block crop = Block.get(cropId, 0, position);
        VillagerPlantEvent event = new VillagerPlantEvent(this, crop);
        this.getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled() && this.level.setBlock(position, crop, true, true)) {
            switch (cropId) {
                case BlockID.WHEAT_BLOCK:
                    this.wheatSeeds--;
                    break;
                case BlockID.CARROT_BLOCK:
                    this.carrotSeeds--;
                    break;
                case BlockID.POTATO_BLOCK:
                    this.potatoSeeds--;
                    break;
                case BlockID.BEETROOT_BLOCK:
                    this.beetrootSeeds--;
                    break;
                default:
                    break;
            }
            this.namedTag.putInt("WheatSeeds", this.wheatSeeds);
            this.namedTag.putInt("CarrotSeeds", this.carrotSeeds);
            this.namedTag.putInt("PotatoSeeds", this.potatoSeeds);
            this.namedTag.putInt("BeetrootSeeds", this.beetrootSeeds);
        }
        this.farmTarget = null;
        this.farmPlantingTarget = false;
        this.farmSearchTicks = 0;
    }

    private void harvestFarmTarget() {
        if (this.farmTarget == null) {
            return;
        }
        int x = this.farmTarget.getFloorX();
        int y = this.farmTarget.getFloorY();
        int z = this.farmTarget.getFloorZ();
        FullChunk chunk = this.level.getChunkIfLoaded(x >> 4, z >> 4);
        if (chunk == null || !chunk.isGenerated()) {
            this.farmTarget = null;
            return;
        }

        Block block = this.level.getBlock(chunk, x, y, z, false);
        if (!(block instanceof BlockCrops) || block.getDamage() < 7) {
            this.farmTarget = null;
            return;
        }

        BlockCrops crop = (BlockCrops) block;
        VillagerHarvestEvent event = new VillagerHarvestEvent(this, crop);
        this.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            this.farmTarget = null;
            return;
        }

        Item[] drops = crop.getDrops(Item.get(Item.AIR));
        int cropId = crop.getId();
        List<Item> worldDrops = new ArrayList<>();
        for (Item drop : drops) {
            if (drop == null || drop.isNull() || drop.getCount() <= 0) {
                continue;
            }
            int count = drop.getCount();
            if (cropId == BlockID.WHEAT_BLOCK && drop.getId() == Item.WHEAT_SEEDS) {
                this.wheatSeeds += count;
            } else if (cropId == BlockID.CARROT_BLOCK && drop.getId() == Item.CARROT) {
                this.carrotSeeds += count;
                this.foodPoints = Math.min(12, this.foodPoints + count);
            } else if (cropId == BlockID.POTATO_BLOCK && drop.getId() == Item.POTATO) {
                this.potatoSeeds += count;
                this.foodPoints = Math.min(12, this.foodPoints + count);
            } else if (cropId == BlockID.BEETROOT_BLOCK && drop.getId() == Item.BEETROOT_SEEDS) {
                this.beetrootSeeds += count;
            } else if (cropId == BlockID.BEETROOT_BLOCK && drop.getId() == Item.BEETROOT) {
                this.foodPoints = Math.min(12, this.foodPoints + count);
            } else {
                worldDrops.add(drop);
            }
        }

        boolean replant = false;
        switch (cropId) {
            case BlockID.WHEAT_BLOCK:
                if (this.wheatSeeds > 0) {
                    this.wheatSeeds--;
                    replant = true;
                }
                break;
            case BlockID.CARROT_BLOCK:
                if (this.carrotSeeds > 0) {
                    this.carrotSeeds--;
                    replant = true;
                }
                break;
            case BlockID.POTATO_BLOCK:
                if (this.potatoSeeds > 0) {
                    this.potatoSeeds--;
                    replant = true;
                }
                break;
            case BlockID.BEETROOT_BLOCK:
                if (this.beetrootSeeds > 0) {
                    this.beetrootSeeds--;
                    replant = true;
                }
                break;
            default:
                break;
        }

        this.level.setBlock(crop, Block.get(Block.AIR), true, true);
        if (replant) {
            this.level.setBlock(new Location(x, y, z, this.level),
                    Block.get(cropId, 0, new Location(x, y, z, this.level)), true, true);
        }
        for (Item drop : worldDrops) {
            this.level.dropItem(crop, drop);
        }

        this.namedTag.putInt("WheatSeeds", this.wheatSeeds);
        this.namedTag.putInt("CarrotSeeds", this.carrotSeeds);
        this.namedTag.putInt("PotatoSeeds", this.potatoSeeds);
        this.namedTag.putInt("BeetrootSeeds", this.beetrootSeeds);
        if (this.foodPoints >= 12 && !this.isInLoveCooldown() && !this.isInLove()) {
            this.foodPoints = 0;
            this.setInLove();
        }
        this.namedTag.putInt("FoodPoints", this.foodPoints);
        this.farmTarget = null;
        this.farmSearchTicks = 0;
    }

    private void shareFoodWithNearbyVillager() {
        for (Entity entity : this.level.getNearbyEntities(this.boundingBox.grow(3, 2, 3), this)) {
            if (!(entity instanceof EntityVillagerV1) || entity.closed || !entity.isAlive()) {
                continue;
            }
            EntityVillagerV1 other = (EntityVillagerV1) entity;
            if (other.isBaby() || other.isInLoveCooldown() || other.isInLove() || other.foodPoints > 4) {
                continue;
            }

            // Four food points are transferred as one bread item the recipient can pick up.
            this.level.dropItem(this, Item.get(Item.BREAD, 0, 1));
            this.foodPoints -= 4;
            return;
        }
    }

    private void pickupBreedingFood() {
        for (Entity entity : this.level.getNearbyEntities(this.boundingBox.grow(1.5, 1, 1.5), this)) {
            if (!(entity instanceof EntityItem) || entity.closed) {
                continue;
            }
            EntityItem dropped = (EntityItem) entity;
            if (dropped.getPickupDelay() > 0) {
                continue;
            }
            Item stack = dropped.getItem();
            if (stack == null || stack.isNull()) {
                continue;
            }
            int value = getBreedingFoodValue(stack);
            if (value <= 0) {
                continue;
            }
            int itemCount = Math.max(1, stack.getCount());
            int itemsNeeded = Math.max(1, (12 - this.foodPoints + value - 1) / value);
            int consumed = Math.min(itemCount, itemsNeeded);
            this.foodPoints += value * consumed;

            if (itemCount > consumed) {
                Item remainder = Item.get(stack.getId(), stack.getDamage(), itemCount - consumed);
                this.level.dropItem(dropped, remainder);
            }
            dropped.close();

            if (this.foodPoints >= 12) {
                this.foodPoints = 0;
                this.setInLove();
            }
            return;
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putInt("FoodPoints", this.foodPoints);
    }

    @Override
    public boolean canDespawn() {
        return false;
    }
}

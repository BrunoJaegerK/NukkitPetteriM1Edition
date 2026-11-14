package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.mob.EntityWitch;
import cn.nukkit.entity.mob.EntityMob;
import cn.nukkit.event.entity.CreatureSpawnEvent;
import cn.nukkit.entity.route.WalkerRouteFinder;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.inventory.TradeInventory;
import cn.nukkit.inventory.TradeInventoryRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRule;
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
        this.setMaxHealth(10);
        super.initEntity();

        this.inventory = new TradeInventory(this);
        this.foodPoints = Math.min(12, Math.max(0, this.namedTag.getInt("FoodPoints")));

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
        double nearestDistance = 100.0; // 10 blocks squared
        for (Entity entity : this.level.getNearbyEntities(this.boundingBox.grow(10, 6, 10), this)) {
            if (entity == this || entity.closed || !entity.isAlive() || !(entity instanceof EntityMob)) {
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

    /**
     * Lightweight Bedrock-style daily routine. Work, meeting and rest targets are
     * searched only every 10 seconds and only in already-loaded chunks.
     */
    private void updateRoutineTarget() {
        this.routineSearchTicks = 200;
        long time = Math.floorMod((long) this.level.getTime(), 24000L);
        int[] wantedBlocks = null;

        if (time >= 2000 && time < 9000) {
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
                    if (!containsBlock(wantedBlocks, blockId)
                            || this.isRoutineBlockReserved(nearbyEntities, x + 0.5, y + 1, z + 0.5, blockId)) {
                        continue;
                    }

                    double dx = x + 0.5 - this.x;
                    double dy = y + 1 - this.y;
                    double dz = z + 0.5 - this.z;
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = new Vector3(x + 0.5, y + 1, z + 0.5);
                    }
                }
            }
        }
        return nearest;
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
        if (this.routineSearchTicks > 0) {
            this.routineSearchTicks = Math.max(0, this.routineSearchTicks - Math.max(0, tickDiff));
        }
        if (this.getServer().getMobAiEnabled() && this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING)
                && this.isAlive() && !this.isBaby() && !this.isInLove() && !this.isInLoveCooldown()) {
            this.foodPickupTicks += Math.max(0, tickDiff);
            if (this.foodPickupTicks >= 20) {
                this.foodPickupTicks %= 20;
                this.pickupBreedingFood();
            }
        }
        return updated;
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

package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.item.Item;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.MobEquipmentPacket;

import java.util.UUID;

public class EntityAllay extends EntityFlyingAnimal {

    public static final int NETWORK_ID = 134;
    private static final int ITEM_SEARCH_RANGE_SQUARED = 256;
    private static final int ITEM_PICKUP_RANGE_SQUARED = 4;
    private static final int OWNER_DELIVERY_RANGE_SQUARED = 9;

    private Item heldItem;
    private Item collectedItem;
    private String ownerUuid;
    private EntityItem itemTarget;
    private EntityItem deliveredItem;
    private int deliveredItemIgnoreTicks;
    private int itemSearchCooldown;
    private Vector3 noteBlockTarget;
    private int noteBlockTicks;

    public EntityAllay(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
        this.heldItem = Item.get(Item.AIR);
        this.collectedItem = Item.get(Item.AIR);
        this.loadAllayState();
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
        return 0.6f;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(20);
        super.initEntity();
    }

    private void loadAllayState() {
        if (this.namedTag.contains("AllayItem")) {
            try {
                this.heldItem = NBTIO.getItemHelper(this.namedTag.getCompound("AllayItem"));
            } catch (Exception e) {
                this.server.getLogger().warning("Could not load Allay held item", e);
            }
        }
        if (this.namedTag.contains("AllayCollectedItem")) {
            try {
                this.collectedItem = NBTIO.getItemHelper(this.namedTag.getCompound("AllayCollectedItem"));
            } catch (Exception e) {
                this.server.getLogger().warning("Could not load Allay collected items", e);
            }
        }
        this.ownerUuid = this.namedTag.getString("AllayOwner");
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_INTERESTED, !this.heldItem.isNull());
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        if (!this.heldItem.isNull()) {
            this.namedTag.putCompound("AllayItem", NBTIO.putItemHelper(this.heldItem, -1));
        } else {
            this.namedTag.remove("AllayItem");
        }
        if (!this.collectedItem.isNull()) {
            this.namedTag.putCompound("AllayCollectedItem", NBTIO.putItemHelper(this.collectedItem, -1));
        } else {
            this.namedTag.remove("AllayCollectedItem");
        }
        if (this.ownerUuid != null) {
            this.namedTag.putString("AllayOwner", this.ownerUuid);
        } else {
            this.namedTag.remove("AllayOwner");
        }
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (!this.getServer().getMobAiEnabled()) {
            boolean wasHeadingToNoteBlock = this.noteBlockTarget != null;
            this.itemTarget = null;
            this.followTarget = null;
            if (this.target instanceof EntityItem || this.target instanceof Player || wasHeadingToNoteBlock) {
                this.target = null;
            }
            if (this.noteBlockTarget != null || this.noteBlockTicks > 0) {
                this.noteBlockTarget = null;
                this.noteBlockTicks = 0;
                this.setDataFlag(DATA_FLAGS, DATA_FLAG_DANCING, false);
            }
        } else if (this.followTarget != null && !this.canTarget(this.followTarget)) {
            this.followTarget = null;
        }

        boolean updated = super.onUpdate(currentTick);
        if (this.closed || !this.isAlive() || !this.getServer().getMobAiEnabled()) {
            return updated;
        }

        if (this.itemSearchCooldown > 0) {
            this.itemSearchCooldown--;
        }
        if (this.deliveredItemIgnoreTicks > 0 && --this.deliveredItemIgnoreTicks == 0
                || this.deliveredItem != null && this.deliveredItem.closed) {
            this.deliveredItem = null;
        }
        if (this.noteBlockTicks > 0 && --this.noteBlockTicks == 0) {
            this.noteBlockTarget = null;
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_DANCING, false);
            if (this.followTarget == null && !(this.target instanceof Entity)) {
                this.target = null;
            }
        }

        if (!this.heldItem.isNull() && this.itemSearchCooldown <= 0) {
            this.itemSearchCooldown = 10;
            this.itemTarget = this.findMatchingItem();
            if (this.itemTarget != null) {
                this.followTarget = null;
                this.target = this.itemTarget;
                this.stayTime = 0;
                this.moveTime = 0;
            }
        }

        if (this.itemTarget != null && (this.itemTarget.closed || !this.itemTarget.isAlive()
                || this.itemTarget == this.deliveredItem
                || this.itemTarget.getItem() == null || this.itemTarget.getPickupDelay() > 0
                || !this.matchesHeldItem(this.itemTarget.getItem()))) {
            this.itemTarget = null;
        }

        if (this.itemTarget != null && this.distanceSquared(this.itemTarget) <= ITEM_PICKUP_RANGE_SQUARED) {
            this.pickUpMatchingItem(this.itemTarget);
            this.itemTarget = null;
            this.target = null;
            this.followTarget = null;
        }

        if (!this.collectedItem.isNull()) {
            if (this.noteBlockTarget != null && this.noteBlockTicks > 0) {
                if (this.distanceSquared(this.noteBlockTarget) <= OWNER_DELIVERY_RANGE_SQUARED) {
                    this.deliverCollectedItem(this.noteBlockTarget.add(0, 1, 0));
                }
            } else {
                Player owner = this.getOwnerPlayer();
                if (owner != null && this.distanceSquared(owner) <= OWNER_DELIVERY_RANGE_SQUARED) {
                    this.deliverCollectedItem(owner.add(0, 0.5, 0));
                }
            }
        }

        return updated;
    }

    @Override
    protected void checkTarget() {
        if (!this.getServer().getMobAiEnabled()) {
            this.itemTarget = null;
            this.followTarget = null;
            this.noteBlockTarget = null;
            this.noteBlockTicks = 0;
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_DANCING, false);
            super.checkTarget();
            return;
        }

        Player owner = this.getOwnerPlayer();

        if (!this.heldItem.isNull()) {
            if (this.itemTarget != null && this.itemTarget != this.deliveredItem
                    && !this.itemTarget.closed && this.itemTarget.isAlive()
                    && this.itemTarget.getPickupDelay() <= 0 && this.matchesHeldItem(this.itemTarget.getItem())
                    && this.distanceSquared(this.itemTarget) <= ITEM_SEARCH_RANGE_SQUARED) {
                this.followTarget = null;
                this.target = this.itemTarget;
                return;
            }

            if (this.itemSearchCooldown <= 0) {
                this.itemTarget = this.findMatchingItem();
                this.itemSearchCooldown = 10;
                if (this.itemTarget != null) {
                    this.followTarget = null;
                    this.target = this.itemTarget;
                    this.stayTime = 0;
                    this.moveTime = 0;
                    return;
                }
            }

            if (this.noteBlockTarget != null && this.noteBlockTicks > 0) {
                this.followTarget = null;
                this.target = this.noteBlockTarget.clone();
                return;
            }

            if (owner != null) {
                this.followTarget = owner;
                this.target = owner;
                return;
            }
        }

        if (this.noteBlockTarget != null && this.noteBlockTicks > 0) {
            this.followTarget = null;
            this.target = this.noteBlockTarget.clone();
            return;
        }

        this.itemTarget = null;
        super.checkTarget();
    }

    @Override
    public boolean canTarget(Entity entity) {
        return this.getServer().getMobAiEnabled() && entity instanceof Player && this.ownerUuid != null
                && this.ownerUuid.equals(entity.getUniqueId().toString()) && entity.canBeFollowed();
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        return this.canTarget(creature) && creature.isAlive() && !creature.closed && distance <= 256;
    }

    public void hearNoteBlock(Vector3 noteBlock) {
        if (!this.getServer().getMobAiEnabled() || this.distanceSquared(noteBlock) > ITEM_SEARCH_RANGE_SQUARED) {
            return;
        }
        this.noteBlockTarget = noteBlock.clone();
        this.noteBlockTicks = 600;
        this.followTarget = null;
        this.target = this.noteBlockTarget.clone();
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_DANCING, true);
        this.stayTime = 0;
        this.moveTime = 0;
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (item.getId() == Item.AIR && !this.heldItem.isNull() && this.collectedItem.isNull()) {
            Item returnedItem = this.heldItem.clone();
            if (player.getInventory().canAddItem(returnedItem)) {
                player.getInventory().addItem(returnedItem);
                this.heldItem = Item.get(Item.AIR);
                this.ownerUuid = null;
                this.itemTarget = null;
                this.setDataFlag(DATA_FLAGS, DATA_FLAG_INTERESTED, false);
                this.sendHeldItem();
                this.level.addSound(this, Sound.MOB_ALLAY_ITEM_TAKEN);
                return true;
            }
            return false;
        }

        if (this.heldItem.isNull() && !item.isNull()) {
            this.heldItem = item.clone();
            this.heldItem.setCount(1);
            this.ownerUuid = player.getUniqueId().toString();
            this.itemTarget = null;
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_INTERESTED, true);
            this.sendHeldItem();
            this.level.addSound(this, Sound.MOB_ALLAY_ITEM_GIVEN);
            return true;
        }

        return super.onInteract(player, item, clickedPos);
    }

    @Override
    public void spawnTo(Player player) {
        super.spawnTo(player);
        this.sendHeldItem(player);
    }

    private EntityItem findMatchingItem() {
        EntityItem nearest = null;
        double nearestDistance = ITEM_SEARCH_RANGE_SQUARED;
        for (Entity entity : this.level.getNearbyEntities(this.getBoundingBox().grow(16, 16, 16), this)) {
            if (!(entity instanceof EntityItem) || entity.closed || !entity.isAlive()) {
                continue;
            }
            EntityItem droppedItem = (EntityItem) entity;
            if (droppedItem == this.deliveredItem) {
                continue;
            }
            if (!droppedItem.isAllowNonPlayerPickup() || droppedItem.getPickupDelay() > 0
                    || !this.matchesHeldItem(droppedItem.getItem())) {
                continue;
            }
            double distance = this.distanceSquared(droppedItem);
            if (distance < nearestDistance) {
                nearest = droppedItem;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private boolean matchesHeldItem(Item item) {
        return item != null && !this.heldItem.isNull() && item.equals(this.heldItem, true, true);
    }

    private void pickUpMatchingItem(EntityItem droppedItem) {
        Item droppedStack = droppedItem.getItem();
        if (droppedStack == null || droppedStack.isNull() || droppedItem.getPickupDelay() > 0) {
            return;
        }

        int currentCount = this.collectedItem.isNull() ? 0 : this.collectedItem.getCount();
        int pickedCount = Math.min(droppedStack.getCount(), this.heldItem.getMaxStackSize() - currentCount);
        if (pickedCount <= 0) {
            return;
        }

        this.collectedItem = this.heldItem.clone();
        this.collectedItem.setCount(currentCount + pickedCount);
        int remainder = droppedStack.getCount() - pickedCount;
        if (remainder == 0) {
            droppedStack.setCount(0);
            droppedItem.close();
        } else {
            Item remainderItem = droppedStack.clone();
            remainderItem.setCount(remainder);
            droppedStack.setCount(0);
            droppedItem.close();
            this.level.dropItem(droppedItem, remainderItem);
        }
    }

    private void deliverCollectedItem(Vector3 location) {
        this.deliveredItem = this.level.dropAndGetItem(location, this.collectedItem.clone(),
                new Vector3(0, 0.1, 0), 10);
        this.deliveredItemIgnoreTicks = 100;
        this.collectedItem = Item.get(Item.AIR);
        this.level.addSound(this, Sound.MOB_ALLAY_ITEM_THROWN);
    }

    private Player getOwnerPlayer() {
        if (this.ownerUuid == null || this.ownerUuid.isEmpty()) {
            return null;
        }
        UUID ownerId;
        try {
            ownerId = UUID.fromString(this.ownerUuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
        for (Player player : this.server.getOnlinePlayersList()) {
            if (player.getUniqueId().equals(ownerId) && player.getLevel() == this.level && !player.closed) {
                return player;
            }
        }
        return null;
    }

    private void sendHeldItem() {
        MobEquipmentPacket packet = new MobEquipmentPacket();
        packet.eid = this.getId();
        packet.item = this.heldItem.isNull() ? Item.get(Item.AIR) : this.heldItem.clone();
        Server.broadcastPacket(this.getViewers().values(), packet);
    }

    private void sendHeldItem(Player player) {
        MobEquipmentPacket packet = new MobEquipmentPacket();
        packet.eid = this.getId();
        packet.item = this.heldItem.isNull() ? Item.get(Item.AIR) : this.heldItem.clone();
        player.dataPacket(packet);
    }

    @Override
    public int getKillExperience() {
        return 0;
    }

    @Override
    public boolean canDespawn() {
        return false;
    }
}


package cn.nukkit.event.entity;

import cn.nukkit.block.BlockDoor;
import cn.nukkit.entity.passive.EntityVillagerV1;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;

/**
 * Called when a villager attempts to open a wooden door while navigating.
 */
public class VillagerDoorOpenEvent extends EntityEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final BlockDoor door;

    public VillagerDoorOpenEvent(EntityVillagerV1 villager, BlockDoor door) {
        this.entity = villager;
        this.door = door;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    public BlockDoor getDoor() {
        return this.door;
    }

    @Override
    public EntityVillagerV1 getEntity() {
        return (EntityVillagerV1) super.getEntity();
    }
}

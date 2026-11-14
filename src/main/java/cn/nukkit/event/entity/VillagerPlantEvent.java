package cn.nukkit.event.entity;

import cn.nukkit.block.Block;
import cn.nukkit.entity.passive.EntityVillagerV1;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;

/**
 * Called when a farmer villager attempts to plant a crop.
 */
public class VillagerPlantEvent extends EntityEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final Block crop;

    public VillagerPlantEvent(EntityVillagerV1 villager, Block crop) {
        this.entity = villager;
        this.crop = crop;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    public Block getCrop() {
        return this.crop;
    }

    @Override
    public EntityVillagerV1 getEntity() {
        return (EntityVillagerV1) super.getEntity();
    }
}

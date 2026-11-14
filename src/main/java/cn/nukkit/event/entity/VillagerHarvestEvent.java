package cn.nukkit.event.entity;

import cn.nukkit.block.BlockCrops;
import cn.nukkit.entity.passive.EntityVillagerV1;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;

/**
 * Called when a farmer villager attempts to harvest a mature crop.
 */
public class VillagerHarvestEvent extends EntityEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final BlockCrops crop;

    public VillagerHarvestEvent(EntityVillagerV1 villager, BlockCrops crop) {
        this.entity = villager;
        this.crop = crop;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    public BlockCrops getCrop() {
        return this.crop;
    }

    @Override
    public EntityVillagerV1 getEntity() {
        return (EntityVillagerV1) super.getEntity();
    }
}

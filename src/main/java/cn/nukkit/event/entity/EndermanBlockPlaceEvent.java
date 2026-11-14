package cn.nukkit.event.entity;

import cn.nukkit.block.Block;
import cn.nukkit.entity.mob.EntityEnderman;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;

/**
 * Called when an Enderman attempts to place the block it is carrying.
 */
public class EndermanBlockPlaceEvent extends EntityEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final Block block;

    public EndermanBlockPlaceEvent(EntityEnderman entity, Block block) {
        this.entity = entity;
        this.block = block;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    public Block getBlock() {
        return this.block;
    }

    @Override
    public EntityEnderman getEntity() {
        return (EntityEnderman) super.getEntity();
    }
}

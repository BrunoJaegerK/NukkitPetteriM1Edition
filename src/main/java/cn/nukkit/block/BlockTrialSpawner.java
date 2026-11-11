package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.material.BlockType;

/**
 * Trial Spawner (minecraft:trial_spawner), found in trial chambers.
 * Hardness 50, blast resistance 50, no assigned tool, drops nothing (even with Silk Touch), cannot be pushed.
 * Mob spawning and the ominous/active states are not implemented yet.
 */
public class BlockTrialSpawner extends BlockTransparent {

    @Override
    public String getName() {
        return "Trial Spawner";
    }

    @Override
    public int getId() {
        return TRIAL_SPAWNER;
    }

    @Override
    public double getHardness() {
        return 50;
    }

    @Override
    public double getResistance() {
        return 50;
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[0];
    }

    @Override
    public boolean canSilkTouch() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.STONE_BLOCK_COLOR;
    }

    @Override
    public int getMinimumVersion() {
        return ProtocolInfo.v1_21_0;
    }

    @Override
    public BlockType getAlternateBlock(int protocol) {
        return BlockTypes.STONE;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (super.place(item, block, target, face, fx, fy, fz, player)) {
            BlockEntity.createBlockEntity(BlockEntity.TRIAL_SPAWNER, this.getChunk(), BlockEntity.getDefaultCompound(this, BlockEntity.TRIAL_SPAWNER));
            return true;
        }
        return false;
    }
}

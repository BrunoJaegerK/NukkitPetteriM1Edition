package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.material.BlockType;

/**
 * Heavy Core (minecraft:heavy_core), found in ominous vaults. Used to craft the mace.
 * Hardness 10, blast resistance 1200, no light, a single block state.
 */
public class BlockHeavyCore extends BlockSolid {

    @Override
    public String getName() {
        return "Heavy Core";
    }

    @Override
    public int getId() {
        return HEAVY_CORE;
    }

    @Override
    public double getHardness() {
        return 10;
    }

    @Override
    public double getResistance() {
        return 1200;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public boolean canHarvestWithHand() {
        // Can be mined with any tool or by hand (a pickaxe is only the fastest way)
        return true;
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{this.toItem()};
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.IRON_BLOCK_COLOR;
    }

    @Override
    public int getMinimumVersion() {
        return ProtocolInfo.v1_21_0;
    }

    @Override
    public BlockType getAlternateBlock(int protocol) {
        return BlockTypes.OBSIDIAN;
    }
}

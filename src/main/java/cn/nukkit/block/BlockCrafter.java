package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.BlockFace;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.material.BlockType;

/**
 * Crafter (minecraft:crafter).
 * Hardness 1.5, blast resistance 3.5, drops itself, a pickaxe is the fastest tool.
 * Only the default block state exists in the runtime palette, so orientation, crafting and triggered states are not synced yet.
 */
public class BlockCrafter extends BlockSolid {

    @Override
    public String getName() {
        return "Crafter";
    }

    @Override
    public int getId() {
        return CRAFTER;
    }

    @Override
    public double getHardness() {
        return 1.5;
    }

    @Override
    public double getResistance() {
        return 3.5;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public boolean canHarvestWithHand() {
        return true;
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{this.toItem()};
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
        return BlockTypes.CRAFTING_TABLE;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (super.place(item, block, target, face, fx, fy, fz, player)) {
            BlockEntity.createBlockEntity(BlockEntity.CRAFTER, this.getChunk(), BlockEntity.getDefaultCompound(this, BlockEntity.CRAFTER));
            return true;
        }
        return false;
    }
}

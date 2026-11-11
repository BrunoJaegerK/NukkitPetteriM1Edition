package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.material.BlockType;

/**
 * Vault (minecraft:vault), found in trial chambers.
 * Hardness 50, blast resistance 50, no assigned tool, drops nothing, light level 6.
 * Only the default (inactive) block state exists in the runtime palette, so unlocking and ejecting are not implemented yet.
 */
public class BlockVault extends BlockTransparent {

    @Override
    public String getName() {
        return "Vault";
    }

    @Override
    public int getId() {
        return VAULT;
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
    public int getLightLevel() {
        return 6; // 12 while unlocking or ejecting
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
    public BlockColor getColor() {
        return BlockColor.GRAY_BLOCK_COLOR;
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
            BlockEntity.createBlockEntity(BlockEntity.VAULT, this.getChunk(), BlockEntity.getDefaultCompound(this, BlockEntity.VAULT));
            return true;
        }
        return false;
    }
}

package mekanism.common.block.states;

import javax.annotation.Nonnull;
import mekanism.common.base.IActiveState;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldReader;

//TODO: Should/Can IActiveSate be merged with this overriding this. (Will look at when moving some TileEntity stuff into blocks/block states more directly)
public interface IStateActive {

    default boolean isActive(@Nonnull BlockState state, @Nonnull IWorldReader world, @Nonnull BlockPos pos) {
        TileEntity tile = MekanismUtils.getTileEntitySafe(world, pos);
        return tile != null && isActive(tile);
    }

    default boolean isActive(@Nonnull TileEntity tile) {
        if (tile instanceof IActiveState) {
            return ((IActiveState) tile).getActive();
        }
        return false;
    }
}
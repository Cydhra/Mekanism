package mekanism.common.tile;

import javax.annotation.Nonnull;
import mekanism.api.Coord4D;
import mekanism.api.lasers.ILaserReceptor;
import mekanism.common.LaserManager;
import mekanism.common.LaserManager.LaserInfo;
import mekanism.common.Mekanism;
import mekanism.common.MekanismBlock;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.InventoryUtils;
import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;

public class TileEntityLaser extends TileEntityMekanism {

    public Coord4D digging;
    public double diggingProgress;

    public TileEntityLaser() {
        super(MekanismBlock.LASER);
    }

    @Override
    public void onUpdate() {
        if (world.isRemote) {
            if (getActive()) {
                BlockRayTraceResult mop = LaserManager.fireLaserClient(this, getDirection(), MekanismConfig.usage.laser.get(), world);
                Coord4D hitCoord = mop == null ? null : new Coord4D(mop, world);
                if (hitCoord == null || !hitCoord.equals(digging)) {
                    digging = hitCoord;
                    diggingProgress = 0;
                }
                if (hitCoord != null) {
                    BlockState blockHit = hitCoord.getBlockState(world);
                    TileEntity tileHit = hitCoord.getTileEntity(world);
                    float hardness = blockHit.getBlockHardness(world, hitCoord.getPos());
                    if (hardness >= 0 && !CapabilityUtils.getCapabilityHelper(tileHit, Capabilities.LASER_RECEPTOR_CAPABILITY, mop.getFace()).matches(ILaserReceptor::canLasersDig)) {
                        diggingProgress += MekanismConfig.usage.laser.get();
                        if (diggingProgress < hardness * MekanismConfig.general.laserEnergyNeededPerHardness.get()) {
                            Mekanism.proxy.addHitEffects(hitCoord, mop);
                        }
                    }
                }
            }
        } else {
            if (getEnergy() >= MekanismConfig.usage.laser.get()) {
                setActive(true);
                LaserInfo info = LaserManager.fireLaser(this, getDirection(), MekanismConfig.usage.laser.get(), world);
                Coord4D hitCoord = info.movingPos == null ? null : new Coord4D(info.movingPos, world);

                if (hitCoord == null || !hitCoord.equals(digging)) {
                    digging = hitCoord;
                    diggingProgress = 0;
                }
                if (hitCoord != null) {
                    BlockState blockHit = hitCoord.getBlockState(world);
                    TileEntity tileHit = hitCoord.getTileEntity(world);
                    float hardness = blockHit.getBlockHardness(world, hitCoord.getPos());
                    if (hardness >= 0 && !CapabilityUtils.getCapabilityHelper(tileHit, Capabilities.LASER_RECEPTOR_CAPABILITY, info.movingPos.getFace())
                          .matches(receptor -> !receptor.canLasersDig())) {
                        diggingProgress += MekanismConfig.usage.laser.get();
                        if (diggingProgress >= hardness * MekanismConfig.general.laserEnergyNeededPerHardness.get()) {
                            LaserManager.breakBlock(hitCoord, true, world, pos);
                            diggingProgress = 0;
                        }
                    }
                }
                setEnergy(getEnergy() - MekanismConfig.usage.laser.get());
            } else {
                setActive(false);
                diggingProgress = 0;
            }
        }
    }

    @Override
    public boolean canReceiveEnergy(Direction side) {
        return side == getOppositeDirection();
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull Direction side) {
        return InventoryUtils.EMPTY;
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, Direction side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        return super.isCapabilityDisabled(capability, side);
    }
}
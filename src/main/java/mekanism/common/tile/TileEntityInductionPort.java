package mekanism.common.tile;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import mekanism.api.Coord4D;
import mekanism.api.IConfigurable;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.text.EnumColor;
import mekanism.common.MekanismLang;
import mekanism.common.base.IEnergyWrapper;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.CapabilityWrapperManager;
import mekanism.common.integration.forgeenergy.ForgeEnergyIntegration;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.util.CableUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.text.BooleanStateDisplay.InputOutput;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;

public class TileEntityInductionPort extends TileEntityInductionCasing implements IConfigurable {

    private CapabilityWrapperManager<IEnergyWrapper, ForgeEnergyIntegration> forgeEnergyManager = new CapabilityWrapperManager<>(IEnergyWrapper.class, ForgeEnergyIntegration.class);

    public TileEntityInductionPort() {
        super(MekanismBlocks.INDUCTION_PORT);
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        if (structure != null && getActive()) {
            CableUtils.emit(this);
        }
    }

    @Override
    public boolean canOutputEnergy(Direction side) {
        if (structure != null && getActive()) {
            return !structure.locations.contains(Coord4D.get(this).offset(side));
        }
        return false;
    }

    @Override
    public boolean canReceiveEnergy(Direction side) {
        return structure != null && !getActive();
    }

    @Override
    public double getMaxOutput() {
        return structure != null ? structure.getRemainingOutput() : 0;
    }

    @Override
    public double acceptEnergy(Direction side, double amount, boolean simulate) {
        return side == null || canReceiveEnergy(side) ? addEnergy(amount, simulate) : 0;
    }

    @Override
    public double pullEnergy(Direction side, double amount, boolean simulate) {
        return side == null || canOutputEnergy(side) ? removeEnergy(amount, simulate) : 0;
    }

    @Override
    public ActionResultType onSneakRightClick(PlayerEntity player, Direction side) {
        if (!isRemote()) {
            boolean oldMode = getActive();
            setActive(!oldMode);
            player.sendMessage(MekanismLang.LOG_FORMAT.translateColored(EnumColor.DARK_BLUE, MekanismLang.MEKANISM,
                  MekanismLang.INDUCTION_PORT_MODE.translateColored(EnumColor.GRAY, InputOutput.of(oldMode, true))));
        }
        return ActionResultType.SUCCESS;
    }

    @Override
    public ActionResultType onRightClick(PlayerEntity player, Direction side) {
        return ActionResultType.PASS;
    }

    @Override
    public boolean renderUpdate() {
        return true;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapabilityIfEnabled(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (capability == Capabilities.CONFIGURABLE_CAPABILITY) {
            return Capabilities.CONFIGURABLE_CAPABILITY.orEmpty(capability, LazyOptional.of(() -> this));
        } else if (capability == Capabilities.ENERGY_STORAGE_CAPABILITY) {
            return Capabilities.ENERGY_STORAGE_CAPABILITY.orEmpty(capability, LazyOptional.of(() -> this));
        } else if (capability == Capabilities.ENERGY_ACCEPTOR_CAPABILITY) {
            return Capabilities.ENERGY_ACCEPTOR_CAPABILITY.orEmpty(capability, LazyOptional.of(() -> this));
        } else if (capability == Capabilities.ENERGY_OUTPUTTER_CAPABILITY) {
            return Capabilities.ENERGY_OUTPUTTER_CAPABILITY.orEmpty(capability, LazyOptional.of(() -> this));
        } else if (capability == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.orEmpty(capability, LazyOptional.of(() -> forgeEnergyManager.getWrapper(this, side)));
        }
        return super.getCapabilityIfEnabled(capability, side);
    }

    @Nonnull
    @Override
    public List<IInventorySlot> getInventorySlots(@Nullable Direction side) {
        if (side == null) {
            //Allow internal queries to view/see both all slots, we only limit the slots that can be seen
            // to either input or output if we are querying this with a specific side (as then we are
            // in the world)
            return super.getInventorySlots(null);
        }
        if (!hasInventory() || structure == null) {
            //TODO: Previously we had a check like !isRemote() ? structure == null : !clientHasStructure
            // Do we still need this if we ever actually needed it?
            //If we don't have a structure then return that we have no slots accessible
            return Collections.emptyList();
        }
        //TODO: Cache this??
        return Collections.singletonList(structure.getInventorySlots(side).get(getActive() ? 0 : 1));
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(getEnergy(), getMaxEnergy());
    }
}
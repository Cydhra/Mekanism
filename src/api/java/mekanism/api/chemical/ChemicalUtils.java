package mekanism.api.chemical;

import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.function.IntSupplier;
import javax.annotation.ParametersAreNonnullByDefault;
import mcp.MethodsReturnNonnullByDefault;
import mekanism.api.Action;
import mekanism.api.annotations.NonNull;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.infuse.InfusionStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.PacketBuffer;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ChemicalUtils {

    public static void writeChemicalStack(PacketBuffer buffer, ChemicalStack<?> stack) {
        if (stack.isEmpty()) {
            buffer.writeBoolean(false);
        } else {
            buffer.writeBoolean(true);
            stack.writeToPacket(buffer);
        }
    }

    public static GasStack readGasStack(PacketBuffer buffer) {
        return buffer.readBoolean() ? GasStack.readFromPacket(buffer) : GasStack.EMPTY;
    }

    public static InfusionStack readInfusionStack(PacketBuffer buffer) {
        return buffer.readBoolean() ? InfusionStack.readFromPacket(buffer) : InfusionStack.EMPTY;
    }

    /**
     * Helper to read and load a list of chemical tanks from a {@link ListNBT}
     */
    public static <CHEMICAL extends Chemical<CHEMICAL>, STACK extends ChemicalStack<CHEMICAL>> void readChemicalTanks(List<? extends IChemicalTank<CHEMICAL, STACK>> tanks,
          ListNBT storedTanks) {
        int size = tanks.size();
        for (int tagCount = 0; tagCount < storedTanks.size(); tagCount++) {
            CompoundNBT tagCompound = storedTanks.getCompound(tagCount);
            byte tankID = tagCompound.getByte("Tank");
            if (tankID >= 0 && tankID < size) {
                tanks.get(tankID).deserializeNBT(tagCompound);
            }
        }
    }

    /**
     * Helper to read and load a list of chemical tanks to a {@link ListNBT}
     */
    public static <CHEMICAL extends Chemical<CHEMICAL>, STACK extends ChemicalStack<CHEMICAL>> ListNBT writeChemicalTanks(List<? extends IChemicalTank<CHEMICAL, STACK>> tanks) {
        ListNBT tagList = new ListNBT();
        for (int tank = 0; tank < tanks.size(); tank++) {
            CompoundNBT tagCompound = tanks.get(tank).serializeNBT();
            if (!tagCompound.isEmpty()) {
                tagCompound.putByte("Tank", (byte) tank);
                tagList.add(tagCompound);
            }
        }
        return tagList;
    }

    /**
     * Util method for a generic insert implementation for various handlers. Mainly for internal use only
     */
    public static <CHEMICAL extends Chemical<CHEMICAL>, STACK extends ChemicalStack<CHEMICAL>> STACK insert(STACK stack, Action action, STACK empty,
          IntSupplier tankCount, Int2ObjectFunction<@NonNull STACK> inTankGetter, InsertChemical<CHEMICAL, STACK> insertChemical) {
        int tanks = tankCount.getAsInt();
        if (tanks == 1) {
            return insertChemical.insert(0, stack, action);
        }
        IntList matchingTanks = new IntArrayList();
        IntList emptyTanks = new IntArrayList();
        for (int tank = 0; tank < tanks; tank++) {
            STACK inTank = inTankGetter.get(tank);
            if (inTank.isEmpty()) {
                emptyTanks.add(tank);
            } else if (inTank.isTypeEqual(stack)) {
                matchingTanks.add(tank);
            }
        }
        STACK toInsert = stack;
        //Start by trying to insert into the tanks that have the same type
        for (int tank : matchingTanks) {
            STACK remainder = insertChemical.insert(tank, toInsert, action);
            if (remainder.isEmpty()) {
                //If we have no remaining chemical, return that we fit it all
                return empty;
            }
            //Update what we have left to insert, to be the amount we were unable to insert
            toInsert = remainder;
        }
        for (int tank : emptyTanks) {
            STACK remainder = insertChemical.insert(tank, toInsert, action);
            if (remainder.isEmpty()) {
                //If we have no remaining chemical, return that we fit it all
                return empty;
            }
            //Update what we have left to insert, to be the amount we were unable to insert
            toInsert = remainder;
        }
        return toInsert;
    }

    /**
     * Util method for a generic extraction implementation for various handlers. Mainly for internal use only
     */
    public static <CHEMICAL extends Chemical<CHEMICAL>, STACK extends ChemicalStack<CHEMICAL>> STACK extract(int amount, Action action, STACK empty,
          IntSupplier tankCount, Int2ObjectFunction<@NonNull STACK> inTankGetter, ExtractChemical<CHEMICAL, STACK> extractChemical) {
        int tanks = tankCount.getAsInt();
        if (tanks == 1) {
            return extractChemical.extract(0, amount, action);
        }
        STACK extracted = empty;
        int toDrain = amount;
        for (int tank = 0; tank < tanks; tank++) {
            if (extracted.isEmpty() || extracted.isTypeEqual(inTankGetter.get(tank))) {
                //If there is chemical in the tank that matches the type we have started draining, or we haven't found a type yet
                STACK drained = extractChemical.extract(tank, toDrain, action);
                if (!drained.isEmpty()) {
                    //If we were able to drain something, set it as the type we have extracted/increase how much we have extracted
                    if (extracted.isEmpty()) {
                        extracted = drained;
                    } else {
                        extracted.grow(drained.getAmount());
                    }
                    toDrain -= drained.getAmount();
                    if (toDrain == 0) {
                        //If we are done draining break and return the amount extracted
                        break;
                    }
                    //Otherwise keep looking and attempt to drain more from the handler, making sure that it is of
                    // the same type as we have found
                }
            }
        }
        return extracted;
    }

    /**
     * Util method for a generic extraction implementation for various handlers. Mainly for internal use only
     */
    public static <CHEMICAL extends Chemical<CHEMICAL>, STACK extends ChemicalStack<CHEMICAL>> STACK extract(STACK stack, Action action, STACK empty,
          IntSupplier tankCount, Int2ObjectFunction<@NonNull STACK> inTankGetter, ExtractChemical<CHEMICAL, STACK> extractChemical) {
        int tanks = tankCount.getAsInt();
        if (tanks == 1) {
            STACK inTank = inTankGetter.get(0);
            if (inTank.isEmpty() || !inTank.isTypeEqual(stack)) {
                return empty;
            }
            return extractChemical.extract(0, stack.getAmount(), action);
        }
        STACK extracted = empty;
        int toDrain = stack.getAmount();
        for (int tank = 0; tank < tanks; tank++) {
            if (stack.isTypeEqual(inTankGetter.get(tank))) {
                //If there is chemical in the tank that matches the type we are trying to drain, try to draining from it
                STACK drained = extractChemical.extract(tank, toDrain, action);
                if (!drained.isEmpty()) {
                    //If we were able to drain something, set it as the type we have extracted/increase how much we have extracted
                    if (extracted.isEmpty()) {
                        extracted = drained;
                    } else {
                        extracted.grow(drained.getAmount());
                    }
                    toDrain -= drained.getAmount();
                    if (toDrain == 0) {
                        //If we are done draining break and return the amount extracted
                        break;
                    }
                    //Otherwise keep looking and attempt to drain more from the handler
                }
            }
        }
        return extracted;
    }

    @FunctionalInterface
    public interface InsertChemical<CHEMICAL extends Chemical<CHEMICAL>, STACK extends ChemicalStack<CHEMICAL>> {

        STACK insert(int tank, STACK stack, Action action);
    }

    @FunctionalInterface
    public interface ExtractChemical<CHEMICAL extends Chemical<CHEMICAL>, STACK extends ChemicalStack<CHEMICAL>> {

        STACK extract(int tank, int amount, Action action);
    }
}
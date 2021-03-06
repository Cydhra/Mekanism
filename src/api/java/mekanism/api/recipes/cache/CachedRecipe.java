package mekanism.api.recipes.cache;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import javax.annotation.ParametersAreNonnullByDefault;
import mekanism.api.annotations.FieldsAreNonnullByDefault;
import mekanism.api.recipes.MekanismRecipe;

@FieldsAreNonnullByDefault
@ParametersAreNonnullByDefault
//TODO: JavaDocs
public abstract class CachedRecipe<RECIPE extends MekanismRecipe> {

    protected final RECIPE recipe;

    //Used to check if the CachedRecipeHolder can function (Defaults to true)
    private BooleanSupplier canHolderFunction = () -> true;
    //Sets the active state of the holder (Defaults to doing nothing)
    private BooleanConsumer setActive = active -> {
    };

    //Number of ticks to complete the recipe (Default to finishing after every tick)
    private IntSupplier requiredTicks = () -> 1;
    //Runs any saving that needs to be done when the recipe is done, such as marking the tile dirty. (Defaults to doing nothing)
    private Runnable onFinish = () -> {
    };

    //Energy Information (Default to doing nothing and not being used)
    private DoubleSupplier perTickEnergy = () -> 0;
    private DoubleSupplier storedEnergy = () -> 0;
    private DoubleConsumer useEnergy = energy -> {
    };

    //Applies a function to post process getOperationsThisTick (Defaults to capping at one operation per tick)
    private IntUnaryOperator postProcessOperations = currentMax -> Math.min(1, currentMax);

    //TODO: Once "build" is called, then we allow process to be called
    // or only allow this stuff to be set before the first call to process
    // We should add some sort of checking to ensure people don't call this stuff late, for now I am ignoring that extra safety measure

    /**
     * Ticks the machine has spent processing so far
     */
    private int operatingTicks;
    //TODO: Use this throughout mekanism
    //Allows for cached recipe holders to have handling for when the operating ticks changed (this will be used for allowing the containers to sync the progress)
    private IntConsumer operatingTicksChanged = ticks -> {
    };
    //TODO: We need to sync the operating ticks back to the machines
    // Maybe we should also save/load cached recipes when a machine gets saved
    // If we add a setter for the operating ticks, then this should be relatively simple to have it copy from one machine to the upgrades
    // and also to load what state the recipe was at.

    protected CachedRecipe(RECIPE recipe) {
        this.recipe = recipe;
    }

    public CachedRecipe<RECIPE> setCanHolderFunction(BooleanSupplier canHolderFunction) {
        this.canHolderFunction = canHolderFunction;
        return this;
    }

    //TODO: Rename
    public CachedRecipe<RECIPE> setActive(BooleanConsumer setActive) {
        this.setActive = setActive;
        return this;
    }

    public CachedRecipe<RECIPE> setEnergyRequirements(DoubleSupplier perTickEnergy, DoubleSupplier storedEnergy, DoubleConsumer useEnergy) {
        this.perTickEnergy = perTickEnergy;
        this.storedEnergy = storedEnergy;
        this.useEnergy = useEnergy;
        return this;
    }

    public CachedRecipe<RECIPE> setRequiredTicks(IntSupplier requiredTicks) {
        this.requiredTicks = requiredTicks;
        return this;
    }

    public CachedRecipe<RECIPE> setOperatingTicksChanged(IntConsumer operatingTicksChanged) {
        this.operatingTicksChanged = operatingTicksChanged;
        return this;
    }

    public CachedRecipe<RECIPE> setOnFinish(Runnable onFinish) {
        this.onFinish = onFinish;
        return this;
    }

    public CachedRecipe<RECIPE> setPostProcessOperations(IntUnaryOperator postProcessOperations) {
        this.postProcessOperations = postProcessOperations;
        return this;
    }

    public void process() {
        //TODO: Given we are going to probably have ALL recipes check the getOperationsThisTick(), we are going to
        // want some way to check things so that by default it doesn't do the max operations and instead does a single
        // run for the majority of recipes
        //TODO: Should this be passing Integer.MAX_VALUE or get the value from somewhere else. Some sort of thing the tile passes as a supplier
        int operations = canHolderFunction() ? postProcessOperations.applyAsInt(getOperationsThisTick(Integer.MAX_VALUE)) : 0;
        if (operations > 0) {
            setActive.accept(true);
            //Always use energy, as that is a constant thing we can check
            useEnergy(operations);
            operatingTicks++;
            if (operatingTicks >= getTicksRequired()) {
                operatingTicks = 0;
                finishProcessing(operations);
                onFinish.run();
            } else {
                //If we still have ticks left required to operate, use the contents
                useResources(operations);
            }
            //TODO: Do we want to make it so if required ticks is 1, that this isn't fired as it will be ++ -> 1 then set to zero again
            operatingTicksChanged.accept(operatingTicks);
        } else {
            //TODO: Check performance, previously this only would set it to inactive if the energy the machine had last tick is less than
            // the energy we have now. Due to the performance improvements that were made to handling the active states, I believe that
            // using the more accurate "disabling" of machines makes more sense
            setActive.accept(false);
            if (operations < 0) {
                //Reset the progress
                operatingTicks = 0;
                operatingTicksChanged.accept(operatingTicks);
            }
        }
    }

    public int getOperatingTicks() {
        return operatingTicks;
    }

    private boolean canHolderFunction() {
        return canHolderFunction.getAsBoolean();
    }

    protected double getStoredElectricity() {
        return storedEnergy.getAsDouble();
    }

    protected double getEnergyPerTick() {
        return perTickEnergy.getAsDouble();
    }

    private int getTicksRequired() {
        return requiredTicks.getAsInt();
    }

    protected void useResources(int operations) {
    }

    protected void useEnergy(int operations) {
        useEnergy.accept(operations * getEnergyPerTick());
    }

    //TODO: Is there a better name for this, basically is how many times this can function this tick
    // Also note that the postProcess doesn't auto get run on this
    // Values less than one means the progress will be reset, a value of zero means that no operations will take place
    // but progress will be saved
    protected int getOperationsThisTick(int currentMax) {
        //TODO: Try to deduplicate the code in the implementations as there is a good bit of duplication for calculating the max
        // of the different types that recipe uses
        if (currentMax <= 0) {
            //Short circuit that if we already can't perform any outputs, just return
            return currentMax;
        }
        double energyPerTick = getEnergyPerTick();
        if (energyPerTick == 0) {
            //If we don't have an energy requirement return what we were told the max is
            return currentMax;
        }
        double operations = getStoredElectricity() / energyPerTick;
        //Make sure we don't have any integer overflow in calculating how much we have room for
        return Math.min(operations > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) operations, currentMax);
    }

    public boolean canFunction() {
        //TODO: Decide if we should be passing 1 as the current max or Integer.MAX_VALUE
        // Currently is passing 1, as if anything has something that is based off current operations
        // and short circuits because of it then going to a fractional amount
        return canHolderFunction() && postProcessOperations.applyAsInt(getOperationsThisTick(1)) > 0;
    }

    //TODO: Is there some alternative for how we can check the validity of the input for cached recipe refresh purposes
    public abstract boolean isInputValid();

    //TODO: Check all recipes for properly removing the inputs
    protected abstract void finishProcessing(int operations);

    public RECIPE getRecipe() {
        return recipe;
    }
}
package com.power.morediodes.assembly;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.power.morediodes.registry.ModRecipeTypes;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

/**
 * Runtime side of the fan steps. Called from FanProcessingMixin, which only hands over items whose
 * NEXT sequenced-assembly step is a fan step of the fan type they are currently in. Everything else
 * is left to Create, so normal bulk smelting / smoking / haunting / washing is untouched.
 */
public final class FanSteps {
    private FanSteps() {
    }

    /** The step this item would perform in a fan of this type right now, or null. */
    @Nullable
    public static RecipeHolder<? extends FanStepRecipe> find(Level level, ItemStack stack, FanProcessingType type) {
        if (stack.isEmpty())
            return null;
        if (type == AllFanProcessingTypes.BLASTING)
            return lookup(level, stack, ModRecipeTypes.BLASTING_STEP, FanStepRecipe.Blasting.class);
        if (type == AllFanProcessingTypes.SMOKING)
            return lookup(level, stack, ModRecipeTypes.SMOKING_STEP, FanStepRecipe.Smoking.class);
        if (type == AllFanProcessingTypes.HAUNTING)
            return lookup(level, stack, ModRecipeTypes.HAUNTING_STEP, FanStepRecipe.Haunting.class);
        return null;
    }

    @Nullable
    private static <R extends FanStepRecipe> RecipeHolder<R> lookup(
            Level level, ItemStack stack, ModRecipeTypes.StepType<R> type, Class<R> recipeClass) {
        return SequencedAssemblyRecipe.getRecipe(level, stack, type.recipeType(), recipeClass).orElse(null);
    }

    /** Ticks a stack has to stay in the fan: step time (or Create's fan time) scaled like Create does for big stacks. */
    private static int timeFor(FanStepRecipe step, int stackSize) {
        int base = step.cookTicks() > 0 ? step.cookTicks() : AllConfigs.server().kinetics.fanProcessingTime.get();
        int sizeModifier = ((stackSize - 1) / 16) + 1;
        return base * sizeModifier + 1;
    }

    /** Item entities flying through the air flow. Returns true when a step finished this tick. */
    public static boolean tickEntity(ItemEntity entity, FanProcessingType type,
                                     RecipeHolder<? extends FanStepRecipe> step) {
        ResourceLocation key = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type);
        if (key == null)
            return false;

        CompoundTag nbt = entity.getPersistentData();
        if (!nbt.contains("CreateData"))
            nbt.put("CreateData", new CompoundTag());
        CompoundTag createData = nbt.getCompound("CreateData");
        if (!createData.contains("Processing"))
            createData.put("Processing", new CompoundTag());
        CompoundTag processing = createData.getCompound("Processing");

        // New fan type, or the marker left behind by a finished step: start a fresh timer.
        if (!processing.contains("Type") || AllFanProcessingTypes.parseLegacy(processing.getString("Type")) != type
                || processing.getInt("Time") < 0) {
            processing.putString("Type", key.toString());
            processing.putInt("Time", timeFor(step.value(), entity.getItem().getCount()));
        }

        int remaining = processing.getInt("Time") - 1;
        processing.putInt("Time", remaining);
        if (remaining != 0)
            return false;

        // Step done: drop the timer so a following step (same fan or another) starts clean.
        createData.remove("Processing");

        Level level = entity.level();
        List<ItemStack> stacks = RecipeApplier.applyRecipeOn(level, entity.getItem(), step.value(), false);
        if (stacks.isEmpty()) {
            entity.discard();
            return false;
        }
        entity.setItem(stacks.removeFirst());
        for (ItemStack extra : stacks) {
            ItemEntity spawned = new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), extra);
            spawned.setDeltaMovement(entity.getDeltaMovement());
            level.addFreshEntity(spawned);
        }
        return true;
    }

    /** Items riding a belt under the fan. Mirrors Create's own belt logic, with the step's time. */
    public static TransportedResult tickBelt(TransportedItemStack transported, Level level, FanProcessingType type,
                                             RecipeHolder<? extends FanStepRecipe> step) {
        TransportedResult ignore = TransportedResult.doNothing();

        // First tick in this fan, or the leftover -1 of a step that just finished: (re)start the timer.
        if (transported.processedBy != type || transported.processingTime < 0) {
            transported.processedBy = type;
            transported.processingTime = timeFor(step.value(), transported.stack.getCount());
            return ignore;
        }
        if (transported.processingTime-- > 0)
            return ignore;

        List<ItemStack> stacks = RecipeApplier.applyRecipeOn(level, transported.stack, step.value(), false);
        List<TransportedItemStack> converted = new ArrayList<>();
        for (ItemStack result : stacks) {
            TransportedItemStack next = transported.getSimilar();
            next.stack = result.copy();
            converted.add(next);
        }
        return TransportedResult.convertTo(converted);
    }
}

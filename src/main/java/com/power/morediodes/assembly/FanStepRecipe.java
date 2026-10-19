package com.power.morediodes.assembly;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

import com.power.morediodes.client.FanStepSubCategory;
import com.power.morediodes.registry.ModRecipeTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A sequenced-assembly step that is carried out by an Encased Fan.
 *
 * JSON (inside "sequence"):
 * <pre>
 * {
 *   "type": "morediodes:blasting_step",
 *   "ingredients": [ { "item": "morediodes:incomplete_matrix" } ],
 *   "results":     [ { "id":   "morediodes:incomplete_matrix" } ],
 *   "processing_time": 200
 * }
 * </pre>
 * "processing_time" is the cooking time in ticks (20 = 1 s). If it is left out, Create's own
 * fan processing time from its server config is used.
 */
@ParametersAreNonnullByDefault
public abstract class FanStepRecipe extends StandardProcessingRecipe<SingleRecipeInput> implements IAssemblyRecipe {

    protected FanStepRecipe(IRecipeTypeInfo typeInfo, ProcessingRecipeParams params) {
        super(typeInfo, params);
    }

    /** The Create fan type (what is behind the fan) that performs this step. */
    public abstract FanProcessingType fanType();

    /** Item shown next to the fan in JEI. */
    public abstract ItemLike catalystIcon();

    /** Base lang key; "<key>.timed" is used when a processing_time is set. */
    protected abstract String langKey();

    /** Cooking time in ticks, 0 = use Create's default fan time. */
    public int cookTicks() {
        return processingDuration;
    }

    // ---- ProcessingRecipe options ----

    @Override
    protected boolean canSpecifyDuration() {
        return true; // without this Create rejects "processing_time" in the JSON
    }

    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        return 2; // first = next assembly item, extras roll as salvage like Create's pressing
    }

    @Override
    public boolean matches(SingleRecipeInput inv, Level level) {
        if (inv.isEmpty())
            return false;
        return ingredients.get(0).test(inv.getItem(0));
    }

    // ---- IAssemblyRecipe ----

    @Override
    public void addAssemblyIngredients(List<Ingredient> list) {
    }

    @Override
    public void addRequiredMachines(Set<ItemLike> list) {
        list.add(AllBlocks.ENCASED_FAN.get());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public Component getDescriptionForAssembly() {
        if (processingDuration > 0)
            return Component.translatable(langKey() + ".timed", formatSeconds(processingDuration));
        return Component.translatable(langKey());
    }

    @Override
    public Supplier<Supplier<SequencedAssemblySubCategory>> getJEISubCategory() {
        return () -> FanStepSubCategory::new;
    }

    private static String formatSeconds(int ticks) {
        return ticks % 20 == 0 ? Integer.toString(ticks / 20) : String.format(Locale.ROOT, "%.1f", ticks / 20f);
    }

    // ---- the three step types ----

    public static class Blasting extends FanStepRecipe {
        public Blasting(ProcessingRecipeParams params) {
            super(ModRecipeTypes.BLASTING_STEP, params);
        }

        @Override
        public FanProcessingType fanType() {
            return AllFanProcessingTypes.BLASTING;
        }

        @Override
        public ItemLike catalystIcon() {
            return Items.LAVA_BUCKET;
        }

        @Override
        protected String langKey() {
            return "morediodes.recipe.assembly.blasting";
        }
    }

    public static class Smoking extends FanStepRecipe {
        public Smoking(ProcessingRecipeParams params) {
            super(ModRecipeTypes.SMOKING_STEP, params);
        }

        @Override
        public FanProcessingType fanType() {
            return AllFanProcessingTypes.SMOKING;
        }

        @Override
        public ItemLike catalystIcon() {
            return Items.CAMPFIRE;
        }

        @Override
        protected String langKey() {
            return "morediodes.recipe.assembly.smoking";
        }
    }

    public static class Haunting extends FanStepRecipe {
        public Haunting(ProcessingRecipeParams params) {
            super(ModRecipeTypes.HAUNTING_STEP, params);
        }

        @Override
        public FanProcessingType fanType() {
            return AllFanProcessingTypes.HAUNTING;
        }

        @Override
        public ItemLike catalystIcon() {
            return Items.SOUL_CAMPFIRE;
        }

        @Override
        protected String langKey() {
            return "morediodes.recipe.assembly.haunting";
        }
    }
}

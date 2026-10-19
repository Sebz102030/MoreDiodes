package com.power.morediodes.registry;

import com.power.morediodes.MoreDiodes;
import com.power.morediodes.assembly.FanStepRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Recipe types for the fan-driven sequenced assembly steps. Use them as the "type" of a step
 * inside a create:sequenced_assembly recipe:
 *
 * <pre>
 *   morediodes:blasting_step   (Encased Fan behind lava / fire-lit blaze burner)
 *   morediodes:smoking_step    (Encased Fan behind fire / campfire)
 *   morediodes:haunting_step   (Encased Fan behind soul fire)
 * </pre>
 */
public final class ModRecipeTypes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MoreDiodes.MODID);
    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, MoreDiodes.MODID);

    public static final StepType<FanStepRecipe.Blasting> BLASTING_STEP =
            step("blasting_step", FanStepRecipe.Blasting::new);
    public static final StepType<FanStepRecipe.Smoking> SMOKING_STEP =
            step("smoking_step", FanStepRecipe.Smoking::new);
    public static final StepType<FanStepRecipe.Haunting> HAUNTING_STEP =
            step("haunting_step", FanStepRecipe.Haunting::new);

    private static <R extends StandardProcessingRecipe<?>> StepType<R> step(
            String name, StandardProcessingRecipe.Factory<R> factory) {
        return new StepType<>(name, factory);
    }

    /** Call once from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
        TYPES.register(modEventBus);
    }

    /** Serializer + recipe type pair; implements Create's IRecipeTypeInfo so ProcessingRecipe accepts it. */
    public static final class StepType<R extends StandardProcessingRecipe<?>> implements IRecipeTypeInfo {
        private final ResourceLocation id;
        private final DeferredHolder<RecipeSerializer<?>, StandardProcessingRecipe.Serializer<R>> serializer;
        private final DeferredHolder<RecipeType<?>, RecipeType<R>> type;

        private StepType(String name, StandardProcessingRecipe.Factory<R> factory) {
            this.id = ResourceLocation.fromNamespaceAndPath(MoreDiodes.MODID, name);
            this.serializer = SERIALIZERS.register(name, () -> new StandardProcessingRecipe.Serializer<>(factory));
            this.type = TYPES.register(name, () -> RecipeType.<R>simple(id));
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends RecipeSerializer<?>> T getSerializer() {
            return (T) serializer.get();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <I extends RecipeInput, X extends Recipe<I>> RecipeType<X> getType() {
            return (RecipeType<X>) (RecipeType<?>) type.get();
        }

        /** The strongly-typed recipe type (use this in lookups instead of getType()). */
        public RecipeType<R> recipeType() {
            return type.get();
        }
    }

    private ModRecipeTypes() {
    }
}

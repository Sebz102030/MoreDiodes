package com.power.morediodes.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.power.morediodes.assembly.FanStepRecipe;
import com.power.morediodes.assembly.FanSteps;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

/**
 * Lets Encased Fans run morediodes:*_step sequenced-assembly steps.
 * Only acts when the item's next assembly step belongs to the fan type it is in; otherwise
 * the original Create code runs unchanged.
 */
@Mixin(value = FanProcessing.class, remap = false)
public abstract class FanProcessingMixin {

    /** Item entities: is there anything to do? (AirCurrent asks this every tick.) */
    @Inject(method = "canProcess(Lnet/minecraft/world/entity/item/ItemEntity;Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void morediodes$canProcess(ItemEntity entity, FanProcessingType type,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (FanSteps.find(entity.level(), entity.getItem(), type) != null)
            cir.setReturnValue(true);
    }

    /** Item entities: count down the step's own time, then advance the assembly. */
    @Inject(method = "applyProcessing(Lnet/minecraft/world/entity/item/ItemEntity;Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void morediodes$applyEntity(ItemEntity entity, FanProcessingType type,
                                               CallbackInfoReturnable<Boolean> cir) {
        RecipeHolder<? extends FanStepRecipe> step = FanSteps.find(entity.level(), entity.getItem(), type);
        if (step != null)
            cir.setReturnValue(FanSteps.tickEntity(entity, type, step));
    }

    /** Items on belts. */
    @Inject(method = "applyProcessing(Lcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;)Lcom/simibubi/create/content/kinetics/belt/behaviour/TransportedItemStackHandlerBehaviour$TransportedResult;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void morediodes$applyBelt(TransportedItemStack transported, Level level, FanProcessingType type,
                                             CallbackInfoReturnable<TransportedResult> cir) {
        RecipeHolder<? extends FanStepRecipe> step = FanSteps.find(level, transported.stack, type);
        if (step != null)
            cir.setReturnValue(FanSteps.tickBelt(transported, level, type, step));
    }
}

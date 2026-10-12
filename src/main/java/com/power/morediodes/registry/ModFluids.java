package com.power.morediodes.registry;

import com.power.morediodes.MoreDiodes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
//file made for fluids

public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, MoreDiodes.MODID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, MoreDiodes.MODID);

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MoreDiodes.MODID);

    public static final DeferredItem<Item> PLACEHOLDER_BUCKET = ITEMS.register("placeholder_bucket",
        () -> new PlaceholderBucketItem(PLACEHOLDER.get(),
                new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    public static final DeferredItem<Item> PLACEHOLDER_BOTTLE = ITEMS.register("placeholder_bottle",
        () -> new Item(new Item.Properties().craftRemainder(Items.GLASS_BOTTLE).stacksTo(4)));

    public static final DeferredHolder<FluidType, FluidType> PLACEHOLDER_TYPE =
            FLUID_TYPES.register("placeholder", () -> new FluidType(FluidType.Properties.create()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> PLACEHOLDER =
            FLUIDS.register("placeholder", () -> new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> PLACEHOLDER_FLOWING =
            FLUIDS.register("placeholder_flowing", () -> new BaseFlowingFluid.Flowing(properties()));

    // A method (not a static field) so the holders above are already initialised when it runs.
    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(PLACEHOLDER_TYPE, PLACEHOLDER, PLACEHOLDER_FLOWING)
            .bucket(PLACEHOLDER_BUCKET);
    }

    private ModFluids() {
    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
    event.registerItem(Capabilities.FluidHandler.ITEM,
            (stack, ctx) -> new FluidBucketWrapper(stack), PLACEHOLDER_BUCKET.get());
    event.registerItem(Capabilities.FluidHandler.ITEM,
            (stack, ctx) -> new PlaceholderBottleHandler(stack), PLACEHOLDER_BOTTLE.get(), Items.GLASS_BOTTLE);
        }
}
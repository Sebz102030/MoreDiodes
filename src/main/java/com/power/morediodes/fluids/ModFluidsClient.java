package com.power.morediodes.fluids;

import com.power.morediodes.MoreDiodes;
import com.power.morediodes.registry.ModFluids;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
//made to register placeholder fluid

@EventBusSubscriber(modid = MoreDiodes.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModFluidsClient {
    private static final ResourceLocation STILL =
            ResourceLocation.fromNamespaceAndPath(MoreDiodes.MODID, "block/placeholder_still");
    private static final ResourceLocation FLOWING =
            ResourceLocation.fromNamespaceAndPath(MoreDiodes.MODID, "block/placeholder_flow");

    @SubscribeEvent
    public static void registerFluidExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() { return STILL; }
            @Override public ResourceLocation getFlowingTexture() { return FLOWING; }
        }, ModFluids.PLACEHOLDER_TYPE.get());
    }
}
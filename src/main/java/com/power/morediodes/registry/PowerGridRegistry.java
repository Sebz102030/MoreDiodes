package com.power.morediodes.registry;

import com.power.morediodes.components.ComponentDiodeWhite;
import com.power.morediodes.MoreDiodes;

import org.patryk3211.powergrid.circuits.schematic.ComponentFootprint;
import org.patryk3211.powergrid.circuits.components.ComponentRegistry;

import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = MoreDiodes.MODID)
class PowerGridRegistry {
    public static final ResourceLocation WHITE_DIODE_ID = id("diode_white");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MoreDiodes.MODID, path);
    }

    private PowerGridRegistry(){}

    private static ComponentDiodeWhite buildDiodeWhite() {
        var footprint = new ComponentFootprint.Builder(2, 2, "component."+MoreDiodes.MODID+".diode_white", null)
            .addPad(0, 0, ComponentDiodeWhite.PIN_ANODE, "ANODE", "ANODE")
            .addPad(1, 1, ComponentDiodeWhite.PIN_KATHODE, "KATHODE", "KATHODE")
            .withItem().withOutline().build();
        return new ComponentDiodeWhite(footprint);}
    
    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(ComponentRegistry.REGISTRY_KEY)){
            event.register(ComponentRegistry.REGISTRY_KEY, WHITE_DIODE_ID, () -> buildDiodeWhite());
            MoreDiodes.LOGGER.info("Registered {} custom powergrid components", 1);
        }
    }
}
package com.power.morediodes.registry;

import com.power.morediodes.components.ComponentDiodeWhite;
import com.power.morediodes.components.ComponentZenerDiode;
import com.power.morediodes.MoreDiodes;

import org.patryk3211.powergrid.circuits.schematic.ComponentFootprint;
import org.patryk3211.powergrid.circuits.components.ComponentRegistry;

import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.world.item.DyeColor;

@EventBusSubscriber(modid = MoreDiodes.MODID)
class PowerGridRegistry {
    public static final ResourceLocation WHITE_DIODE_ID = id("diode_white");
    public static final ResourceLocation BLUE_DIODE_ID = id("diode_blue");
    public static final ResourceLocation YELLOW_DIODE_ID = id("diode_yellow");
    public static final ResourceLocation RED_DIODE_ID = id("diode_red");
    public static final ResourceLocation GREEN_DIODE_ID = id("diode_green");
    public static final ResourceLocation ZENER_DIODE_ID = id("diode_zener");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MoreDiodes.MODID, path);
    }

    private PowerGridRegistry(){}

    private static ComponentDiodeWhite buildDiodeWhite(ComponentDiodeWhite.DiodeColour color, String name) {
        var footprint = new ComponentFootprint.Builder(2, 2, "component."+MoreDiodes.MODID+".diode_"+name, null)
            .addPad(1, 0, ComponentDiodeWhite.PIN_ANODE, "ANODE", "ANODE")
            .addPad(0, 1, ComponentDiodeWhite.PIN_KATHODE, "KATHODE", "KATHODE")
            .withItem().withOutline().build();
        return new ComponentDiodeWhite(footprint, color);}
    
    private static ComponentZenerDiode buildZener() {
        var footprint = new ComponentFootprint.Builder(3, 1, "component."+MoreDiodes.MODID+".diode_zener", null)
            .addPad(0, 0, ComponentZenerDiode.PIN_ANODE, "ANODE", "ANODE")
            .addPad(2, 0, ComponentZenerDiode.PIN_KATHODE, "KATHODE", "KATHODE")
            .withItem().withOutline().build();
        return new ComponentZenerDiode(footprint);}

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(ComponentRegistry.REGISTRY_KEY)){
            event.register(ComponentRegistry.REGISTRY_KEY, WHITE_DIODE_ID, () -> buildDiodeWhite(ComponentDiodeWhite.DiodeColour.white, "white"));
            event.register(ComponentRegistry.REGISTRY_KEY, BLUE_DIODE_ID, () -> buildDiodeWhite(ComponentDiodeWhite.DiodeColour.blue, "blue"));
            event.register(ComponentRegistry.REGISTRY_KEY, YELLOW_DIODE_ID, () -> buildDiodeWhite(ComponentDiodeWhite.DiodeColour.yellow, "yellow"));
            event.register(ComponentRegistry.REGISTRY_KEY, RED_DIODE_ID, () -> buildDiodeWhite(ComponentDiodeWhite.DiodeColour.red, "red"));
            event.register(ComponentRegistry.REGISTRY_KEY, GREEN_DIODE_ID, () -> buildDiodeWhite(ComponentDiodeWhite.DiodeColour.green, "green"));
            event.register(ComponentRegistry.REGISTRY_KEY, ZENER_DIODE_ID, PowerGridRegistry::buildZener);
            MoreDiodes.LOGGER.info("Registered {} custom powergrid components", 6);
        }
    }
}
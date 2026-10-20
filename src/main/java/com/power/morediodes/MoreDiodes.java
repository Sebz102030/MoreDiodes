package com.power.morediodes;

import com.power.morediodes.registry.ModItems;
import com.power.morediodes.registry.ConfigFile;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.patryk3211.powergrid.PowerGrid;
import net.minecraft.resources.ResourceLocation;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.neoforged.fml.config.ModConfig;
import com.power.morediodes.registry.ModFluids;

@Mod(MoreDiodes.MODID)
public class MoreDiodes {

    public static final String MODID = "morediodes";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static final PartialModel GLOW       = partialModel("component/light_diode_glow");
    public static final PartialModel GLOW_DYED  = partialModel("component/light_diode_glow_dyed");

    private static final ResourceKey<CreativeModeTab> POWERGRID_MAIN_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, PowerGrid.asResource("main"));

    public MoreDiodes(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, ConfigFile.SPEC,
                MODID + "-common.toml");
        ModItems.ITEMS.register(modEventBus);
        ModItems.HIDDEN_ITEMS.register(modEventBus);
        ModFluids.register(modEventBus); // fluid types, fluids, buckets/bottles and their capabilities

        // THIS is the line that actually wires items into PowerGrid's tab -
        // without it, ModItems being registered has no effect on the tab.
        modEventBus.addListener(this::addItemsToPowerGridTab);

        LOGGER.info("More diodes initializing");
        LOGGER.info("More diodes initialized {}", ResourceLocation.fromNamespaceAndPath(MODID, "component/light_diode_glow_dyed"));
    }

    private void addItemsToPowerGridTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != POWERGRID_MAIN_TAB)
            return;

        for (var entry : ModItems.ITEMS.getEntries()) {
            event.accept(entry.get());
            //event.accept(ModItems.DIODE_WHITW.get());
        }
        // Fluid buckets / bottles (remove this loop if they should not be in the creative tab).
        for (var entry : ModFluids.ITEMS.getEntries())
            event.accept(entry.get());
    }
    //LOGGER.info("More diodes initialized {}", ResourceLocation.fromNamespaceAndPath(MODID, "component/light_diode_glow_dyed"));

    public static PartialModel partialModel(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(MODID, path));
    }
}
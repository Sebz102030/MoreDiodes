package com.power.morediodes;

import com.power.morediodes.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MoreDiodes.MODID)
public class MoreDiodes {

    public static final String MODID = "morediodes";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public MoreDiodes(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);

        LOGGER.info("More diodes initializing");
    }
}
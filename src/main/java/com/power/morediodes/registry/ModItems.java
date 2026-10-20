package com.power.morediodes.registry;

import com.power.morediodes.MoreDiodes;
import com.power.morediodes.items.IncompletePlaceholder;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MoreDiodes.MODID);
    public static final DeferredRegister.Items HIDDEN_ITEMS = DeferredRegister.createItems(MoreDiodes.MODID);

    public static final DeferredHolder<Item, Item> DIODE_WHITE = ITEMS.registerSimpleItem("diode_white", new Item.Properties());
    public static final DeferredHolder<Item, Item> DIODE_BLUE = ITEMS.registerSimpleItem("diode_blue", new Item.Properties());
    public static final DeferredHolder<Item, Item> DIODE_YELLOW = ITEMS.registerSimpleItem("diode_yellow", new Item.Properties());
    public static final DeferredHolder<Item, Item> DIODE_RED = ITEMS.registerSimpleItem("diode_red", new Item.Properties());
    public static final DeferredHolder<Item, Item> DIODE_GREEN = ITEMS.registerSimpleItem("diode_green", new Item.Properties());
    public static final DeferredHolder<Item, Item> DIODE_ZENER = ITEMS.registerSimpleItem("diode_zener", new Item.Properties());

    public static final DeferredHolder<Item, Item> LED_MATRIX = ITEMS.registerSimpleItem("led_matrix", new Item.Properties());
    public static final DeferredHolder<Item, Item> LED_MATRIX_ADVANCED = ITEMS.registerSimpleItem("led_matrix_advanced", new Item.Properties());

    public static final DeferredHolder<Item, Item> SEVEN_SEGMENT_ANODE = ITEMS.registerSimpleItem("seven_segment_anode", new Item.Properties());
    public static final DeferredHolder<Item, Item> SEVEN_SEGMENT_CATHODE = ITEMS.registerSimpleItem("seven_segment_cathode", new Item.Properties());

    public static final DeferredHolder<Item, Item> INCOMPLETE_MATRIX = HIDDEN_ITEMS.register("incomplete_matrix", ()-> new IncompletePlaceholder(new Item.Properties()));

    private ModItems() {
    }
}
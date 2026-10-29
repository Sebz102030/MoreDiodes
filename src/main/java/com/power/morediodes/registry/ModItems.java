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

    public static final DeferredHolder<Item, Item> LED_BARGRAPH = ITEMS.registerSimpleItem("led_bargraph", new Item.Properties());

    //crafting items
    public static final DeferredHolder<Item, Item> INCOMPLETE_BASE = HIDDEN_ITEMS.register("incomplete_diode_base", ()-> new IncompletePlaceholder(new Item.Properties()));
    public static final DeferredHolder<Item, Item> INCOMPLETE_BARGRAPH = HIDDEN_ITEMS.register("incomplete_led_bargraph", ()-> new IncompletePlaceholder(new Item.Properties()));
    public static final DeferredHolder<Item, Item> INCOMPLETE_MATRIX = HIDDEN_ITEMS.register("incomplete_matrix", ()-> new IncompletePlaceholder(new Item.Properties()));
    public static final DeferredHolder<Item, Item> INCOMPLETE_MATRIX_PART = HIDDEN_ITEMS.register("incomplete_matrix_part", ()-> new IncompletePlaceholder(new Item.Properties()));
    public static final DeferredHolder<Item,Item> INCOMPLETE_ADVANCED_MATRIX_CONTROLLER = HIDDEN_ITEMS.register("incomplete_advanced_matrix_controller", ()-> new IncompletePlaceholder(new Item.Properties()));
    public static final DeferredHolder<Item, Item> INCOMPLETE_SEVENSEGMENT = HIDDEN_ITEMS.register("incomplete_sevensegment", ()-> new IncompletePlaceholder(new Item.Properties()));

    public static final DeferredHolder<Item, Item> FIVE_PIN_BANCH = ITEMS.registerSimpleItem("ten_pin_banch", new Item.Properties());
    public static final DeferredHolder<Item, Item> MULTILED_BASE = ITEMS.registerSimpleItem("multi_light_base", new Item.Properties());
    public static final DeferredHolder<Item, Item> UNCURED_BARGRAPH = ITEMS.registerSimpleItem("uncured_led_bargraph", new Item.Properties());
    public static final DeferredHolder<Item, Item> UNCURED_ADVANCED_MATRIX = ITEMS.registerSimpleItem("uncured_advanced_matrix", new Item.Properties());
    public static final DeferredHolder<Item, Item> UNCURED_MATRIX = ITEMS.registerSimpleItem("uncured_matrix",new Item.Properties());
    public static final DeferredHolder<Item, Item> BAKED_PROCESOR = ITEMS.registerSimpleItem("baked_advanced_controller", new Item.Properties());
    public static final DeferredHolder<Item, Item> CONTROLLER_ADVANCED = ITEMS.registerSimpleItem("undone_advanced_controller", new Item.Properties());
    public static final DeferredHolder<Item, Item> UNCURED_SEVEN_SEGMENT_CATHODE = ITEMS.registerSimpleItem("uncured_seven_segment_cathode", new Item.Properties());
    public static final DeferredHolder<Item, Item> UNCURED_SEVEN_SEGMENT_ANODE = ITEMS.registerSimpleItem("uncured_seven_segment_anode", new Item.Properties());


    private ModItems() {
    }
}
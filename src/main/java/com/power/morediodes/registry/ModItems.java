package com.power.morediodes.registry;

import com.power.morediodes.MoreDiodes;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MoreDiodes.MODID);

    public static final DeferredHolder<Item, Item> DIODE_WHITW = ITEMS.registerSimpleItem("diode_white", new Item.Properties() );
}
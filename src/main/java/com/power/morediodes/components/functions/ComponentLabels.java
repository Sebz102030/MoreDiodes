package com.power.morediodes.components;

import net.minecraft.network.chat.Component;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * Label support for this mod's components. There is deliberately no label property of our own:
 * everything goes through Power Grid's {@code Component.LABEL}, which the component only has
 * if it adds it in its addProperties().
 */
public final class ComponentLabels {
    private ComponentLabels() {
    }

    /** The label text, or an empty string if the component has none. */
    public static String get(PlacedComponent placed) {
        if (!placed.has(org.patryk3211.powergrid.circuits.components.Component.LABEL))
            return "";
        String label = placed.get(org.patryk3211.powergrid.circuits.components.Component.LABEL);
        return label == null ? "" : label;
    }

    /** Adds the label as the first goggle line when one is set. */
    public static void addToTooltip(PlacedComponent placed, List<Component> tooltip) {
        String label = get(placed);
        if (!label.isEmpty())
            Lang.text(label).forGoggles(tooltip);
    }
}

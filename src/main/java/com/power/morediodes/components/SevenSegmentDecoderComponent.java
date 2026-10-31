package com.power.morediodes.components;

import com.google.common.collect.ImmutableCollection;
import com.power.morediodes.MoreDiodes;
import com.power.morediodes.components.functions.ModLang;
import com.power.morediodes.registry.ConfigFile;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.patryk3211.powergrid.circuits.circuitboard.ComponentCircuitBuilder;
import org.patryk3211.powergrid.circuits.components.IComponentGoggleInformation;
import org.patryk3211.powergrid.circuits.components.OrientableComponent;
import org.patryk3211.powergrid.circuits.components.properties.ComponentProperty;
import org.patryk3211.powergrid.circuits.components.properties.IntProperty;
import org.patryk3211.powergrid.circuits.schematic.ComponentFootprint;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;
import org.patryk3211.powergrid.circuits.thermal.ThermalBuilder;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 4-bit to 7-segment decoder: turns a 4-bit number into the hex character 0-F for a 7-segment display.
 * 13 pins, 7 (x) x 3 (y) footprint, one class and one model for two variants:
 * <ul>
 *   <li>common cathode: outputs and inputs are active high (a pin driven above GND is a 1)</li>
 *   <li>common anode: outputs and inputs are active low (a pin pulled below VCC is a 1)</li>
 * </ul>
 * The inputs use the same polarity as the outputs, so a cathode decoder is fed by cathode-type signals.
 *
 * Default orientation: bottom row, left to right: outputs A B C D E F G (node 0..6).
 * Top row, left to right: VCC (node 7), GND (node 8), one empty cell, then D0 (LSB), D1, D2, D3 (MSB)
 * (nodes 9..12).
 *
 * Outputs: every output pin has two switchable resistors, one to VCC and one to GND. A lit segment connects its
 * pin to the supply side (cathode: to VCC, anode: to GND) through a resistance chosen from the supply voltage so
 * about decoderOutputCurrentMilliamps (20 mA) flows into the display segment; an unlit pin is held on the other
 * rail. Without power every output floats. The resistances are changed while the circuit runs, through
 * ElectricWire.setResistance (looked up by reflection; if it is missing the log lists what ElectricWire offers).
 */
public class SevenSegmentDecoderComponent extends OrientableComponent implements IComponentGoggleInformation {

    public static final int PIN_OUT_0 = 0;
    public static final int OUTPUTS = 7;
    public static final int PIN_VCC = 7;
    public static final int PIN_GND = 8;
    public static final int PIN_IN_0 = 9;
    public static final int INPUTS = 4;

    public static final int BASE_WIDTH = 7;
    public static final int BASE_HEIGHT = 3;

    /** Segment bits for the characters 0..F; bit 0 = A ... bit 6 = G. */
    public static final int[] HEX_SEGMENTS = {
            0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07,
            0x7F, 0x6F, 0x77, 0x7C, 0x39, 0x5E, 0x79, 0x71
    };

    private static final float SUPPLY_LOAD_OHMS = 1000.0f;
    private static final float INPUT_LOAD_OHMS = 10000.0f;
    /** Resistance of a switched-off output path. */
    private static final float OFF_OHMS = 10_000_000.0f;
    /** Resistance holding an unlit output on its idle rail. */
    private static final float HOLD_OHMS = 50.0f;
    /** Lowest resistance of a lit output path. */
    private static final float MIN_ON_OHMS = 10.0f;
    /** Supply voltage (V) needed for the decoder to count as powered (10 % tolerance). */
    private static final double MIN_SUPPLY_VOLTAGE = 1.0D;
    private static final double SUPPLY_NEEDED_FRACTION = 0.9D;
    /** An input counts as 1 when it is at least this far (V) from its idle level. */
    private static final double MIN_INPUT_VOLTAGE = 0.5D;

    /** Bits 0..3 = decoded value, bit 4 = powered. Hidden, synced so the goggle tooltip can show it. */
    public static final IntProperty STATE = (IntProperty) new IntProperty(MoreDiodes.MODID, "decoder_state", 0,
            0, 0x1F).hidden().unsafe();

    private final boolean commonAnode;

    public SevenSegmentDecoderComponent(ComponentFootprint footprint, boolean commonAnode) {
        super(footprint);
        this.commonAnode = commonAnode;
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(LABEL);
        properties.add(STATE);
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder,
            ThermalBuilder.@NotNull IEmitter thermals) {
        FloatingNode vcc = builder.terminalNode(PIN_VCC);
        FloatingNode gnd = builder.terminalNode(PIN_GND);

        // VCC-GND load: its current tells whether the supply is connected and high enough.
        ElectricWire supply = builder.connect(SUPPLY_LOAD_OHMS, vcc, gnd);

        // Active high (cathode): a pull-down to GND, a high pin pushes current into it.
        // Active low (anode): a pull-up from VCC, a low pin lets current flow out of it.
        // Either way a floating pin draws no current and reads 0.
        ElectricWire[] inputs = new ElectricWire[INPUTS];
        for (int i = 0; i < INPUTS; i++) {
            FloatingNode pin = builder.terminalNode(PIN_IN_0 + i);
            inputs[i] = commonAnode ? builder.connect(INPUT_LOAD_OHMS, vcc, pin)
                    : builder.connect(INPUT_LOAD_OHMS, pin, gnd);
        }

        // Output pins: a path to VCC and a path to GND, both starting switched off.
        ElectricWire[] toVcc = new ElectricWire[OUTPUTS];
        ElectricWire[] toGnd = new ElectricWire[OUTPUTS];
        for (int i = 0; i < OUTPUTS; i++) {
            FloatingNode out = builder.terminalNode(PIN_OUT_0 + i);
            toVcc[i] = builder.connect(OFF_OHMS, vcc, out);
            toGnd[i] = builder.connect(OFF_OHMS, out, gnd);
        }

        placed.customData = new Runtime(supply, inputs, toVcc, toGnd);
    }

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        if (placed.isClient())
            return false;
        if (!(placed.customData instanceof Runtime rt))
            return true;

        boolean powered = rt.supply.current() >= SUPPLY_NEEDED_FRACTION * MIN_SUPPLY_VOLTAGE / SUPPLY_LOAD_OHMS;
        int value = 0;
        if (powered) {
            for (int i = 0; i < INPUTS; i++) {
                if (rt.inputs[i].current() >= MIN_INPUT_VOLTAGE / INPUT_LOAD_OHMS)
                    value |= 1 << i;
            }
        }
        driveOutputs(rt, powered, powered ? HEX_SEGMENTS[value] : 0);
        int state = value | (powered ? 0x10 : 0);
        if (state != placed.get(STATE)) {
            placed.set(STATE, state);
            placed.notifyClients(STATE);
        }
        return true;
    }

    /** Sets the output resistors for the segment pattern; only touches the circuit when something changed. */
    private void driveOutputs(Runtime rt, boolean powered, int segments) {
        float supplyVolts = (float) (rt.supply.current() * SUPPLY_LOAD_OHMS);
        float activation = (float) (double) ConfigFile.SEGMENT_ACTIVATION_VOLTAGE.get();
        float amps = (float) (ConfigFile.DECODER_OUTPUT_CURRENT_MA.get() / 1000.0D);
        // Resistance that lets about `amps` flow once the segment itself takes its activation voltage.
        float onOhms = powered ? Math.max(MIN_ON_OHMS, (supplyVolts - activation) / amps) : OFF_OHMS;

        boolean sameOhms = Math.abs(onOhms - rt.lastOnOhms) <= 0.05f * rt.lastOnOhms;
        if (rt.drivenPowered == powered && rt.drivenSegments == segments && sameOhms)
            return;
        if (!ResistanceAccess.available()) {
            if (!rt.warned) {
                rt.warned = true;
                ResistanceAccess.warnMissing();
            }
            return;
        }

        for (int i = 0; i < OUTPUTS; i++) {
            boolean lit = (segments & (1 << i)) != 0;
            float vcc, gnd;
            if (!powered) {
                vcc = OFF_OHMS;
                gnd = OFF_OHMS;
            } else if (!commonAnode) {
                // Active high: lit = pushed up to VCC, unlit = held at GND.
                vcc = lit ? onOhms : OFF_OHMS;
                gnd = lit ? OFF_OHMS : HOLD_OHMS;
            } else {
                // Active low: lit = pulled down to GND, unlit = held at VCC.
                gnd = lit ? onOhms : OFF_OHMS;
                vcc = lit ? OFF_OHMS : HOLD_OHMS;
            }
            ResistanceAccess.set(rt.toVcc[i], vcc);
            ResistanceAccess.set(rt.toGnd[i], gnd);
        }
        rt.drivenPowered = powered;
        rt.drivenSegments = segments;
        rt.lastOnOhms = onOhms;
    }

    /** Finds ElectricWire's resistance setter once (by reflection, so a different name only logs instead of failing). */
    private static final class ResistanceAccess {
        private static Method setter;
        private static boolean floatArg;
        private static boolean searched;

        static boolean available() {
            if (!searched) {
                searched = true;
                for (Class<?> c = ElectricWire.class; c != null && setter == null; c = c.getSuperclass()) {
                    for (Class<?> arg : new Class<?>[] { float.class, double.class }) {
                        try {
                            setter = c.getMethod("setResistance", arg);
                            floatArg = arg == float.class;
                            break;
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                }
                if (setter != null)
                    MoreDiodes.LOGGER.info("Decoder outputs use {}", setter);
            }
            return setter != null;
        }

        static void set(ElectricWire wire, float ohms) {
            try {
                setter.invoke(wire, floatArg ? (Object) ohms : (Object) (double) ohms);
            } catch (ReflectiveOperationException e) {
                MoreDiodes.LOGGER.error("Decoder could not set a resistance: {}", e.toString());
            }
        }

        /** Lists what ElectricWire offers, so the right call can be found. */
        static void warnMissing() {
            MoreDiodes.LOGGER.warn("Decoder: ElectricWire has no setResistance(float/double); outputs stay off.");
            for (Class<?> c = ElectricWire.class; c != null && c != Object.class; c = c.getSuperclass()) {
                MoreDiodes.LOGGER.warn("Decoder API dump, class {}:", c.getName());
                for (Method m : c.getDeclaredMethods())
                    MoreDiodes.LOGGER.warn("   {}", m.toGenericString());
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(@NotNull PlacedComponent placed, @NotNull List<Component> tooltip,
            boolean isPlayerSneaking) {
        int state = placed.get(STATE);
        ComponentLabels.addToTooltip(placed, tooltip);
        ModLang.text(commonAnode ? "item.morediodes.seven_segment_decoder_anode"
                : "item.morediodes.seven_segment_decoder_cathode").forGoggles(tooltip);
        if ((state & 0x10) == 0) {
            ModLang.text("tooltip.morediodes.decoder_unpowered").forGoggles(tooltip);
        } else {
            int value = state & 0xF;
            String bits = String.format("%4s", Integer.toBinaryString(value)).replace(' ', '0');
            ModLang.text("tooltip.morediodes.decoder_input", Integer.toHexString(value).toUpperCase() + " (" + bits + ")")
                    .forGoggles(tooltip);
        }
        return true;
    }

    /** Per-placement live wires, stashed via {@link PlacedComponent#customData}. */
    private static final class Runtime {
        final ElectricWire supply;
        final ElectricWire[] inputs;
        final ElectricWire[] toVcc;
        final ElectricWire[] toGnd;
        boolean drivenPowered;
        int drivenSegments = -1;
        float lastOnOhms = OFF_OHMS;
        boolean warned;

        Runtime(ElectricWire supply, ElectricWire[] inputs, ElectricWire[] toVcc, ElectricWire[] toGnd) {
            this.supply = supply;
            this.inputs = inputs;
            this.toVcc = toVcc;
            this.toGnd = toGnd;
        }
    }
}

package com.power.morediodes.components;

import com.google.common.collect.ImmutableCollection;
import com.power.morediodes.MoreDiodes;
import org.jetbrains.annotations.NotNull;
import org.patryk3211.powergrid.circuits.circuitboard.ComponentCircuitBuilder;
import org.patryk3211.powergrid.circuits.components.OrientableComponent;
import org.patryk3211.powergrid.circuits.components.properties.ComponentProperty;
import org.patryk3211.powergrid.circuits.components.properties.FloatProperty;
import org.patryk3211.powergrid.circuits.schematic.ComponentFootprint;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;
import org.patryk3211.powergrid.circuits.thermal.ThermalBuilder;
import org.patryk3211.powergrid.electricity.sim.special.PNJunctionWire;

/**
 * Zener diode.
 *
 *  - Forward (anode -> cathode): behaves like a normal silicon diode (fixed FORWARD_VOLTAGE drop).
 *  - Reverse (cathode -> anode): blocks until the voltage reaches the REVERSE_VOLTAGE property,
 *    then current starts to flow and the voltage is clamped close to it (the "zener voltage").
 *  - Burnout is thermal only: dissipating more than MAX_POWER heats the part past its overheat
 *    temperature and PowerGrid's thermal unit destroys it (same approach as the LEDs).
 */
public class ComponentZenerDiode extends OrientableComponent {
    public static final int PIN_ANODE   = 0;
    public static final int PIN_KATHODE = 1;

    // ---- Editable properties (shown in the component GUI) ----------------------------------
    /** Reverse (zener / breakdown) voltage in V: reverse voltage at which current starts to flow. */
    public static final FloatProperty REVERSE_VOLTAGE =
            new FloatProperty(MoreDiodes.MODID, "reverse_voltage", 5.1f, 1f, 100f);

    // ---- Fixed characteristics --------------------------------------------------------------
    /** Power rating (W). Dissipating more than this long enough destroys the diode. */
    public static final float MAX_POWER = 0.5f;
    /** Overheat temperature (C). */
    public static final float OVERHEAT_TEMPERATURE = 150f;

    /** Fixed forward drop (V) of the diode when conducting anode -> cathode. */
    public static final float FORWARD_VOLTAGE = 0.7f;

    /** Test current (A) at which the forward drop equals FORWARD_VOLTAGE. */
    private static final double FORWARD_TEST_CURRENT = 0.005;
    /** Test current (A) at which the reverse voltage equals REVERSE_VOLTAGE. */
    private static final double ZENER_TEST_CURRENT = 0.005;

    private static final double SERIES_RESISTANCE = 0.5;
    private static final double IDEALITY = 2.0;
    private static final double BREAKDOWN_SATURATION = 1e-6;
    private static final double TEMPERATURE_C = 22.0;

    public ComponentZenerDiode(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(REVERSE_VOLTAGE, power(MAX_POWER));
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, ThermalBuilder.@NotNull IEmitter thermals) {
        double vPass = FORWARD_VOLTAGE;
        double vZener = placed.get(REVERSE_VOLTAGE);

        double vt = 1.380649e-23 * (TEMPERATURE_C + 273.15) / 1.602176634e-19;
        double nVt = IDEALITY * vt;

        // Forward: choose Is so the drop is exactly vPass at FORWARD_TEST_CURRENT
        //   V = n*Vt*ln(I/Is + 1) + I*Rs
        double saturation = FORWARD_TEST_CURRENT / Math.expm1((vPass - FORWARD_TEST_CURRENT * SERIES_RESISTANCE) / nVt);

        // Reverse: the breakdown is a second, mirrored diode curve that starts at -breakdownVoltage.
        // Shift it so the reverse voltage is exactly vZener at ZENER_TEST_CURRENT.
        double overshoot = nVt * Math.log(ZENER_TEST_CURRENT / BREAKDOWN_SATURATION + 1.0)
                + ZENER_TEST_CURRENT * SERIES_RESISTANCE;
        double breakdownVoltage = Math.max(vZener - overshoot, 0.05);

        // node1 = anode, node2 = cathode -> potentialDifference() > 0 when forward biased.
        var wire = new PNJunctionWire(saturation, SERIES_RESISTANCE, TEMPERATURE_C, IDEALITY,
                breakdownVoltage, BREAKDOWN_SATURATION,
                builder.terminalNode(PIN_ANODE), builder.terminalNode(PIN_KATHODE));
        builder.add(wire);
        placed.add(wire);

        // Thermal-only burnout. MAX_POWER settles 5 C below overheat, anything more destroys it.
        // No temperature callback into the junction, so the characteristics stay fixed.
        thermals.builder()
                .addHeatSource(wire)
                .setThermalMass(0.01f)
                .setMaxPower(MAX_POWER, OVERHEAT_TEMPERATURE - 5f)
                .setOverheatTemperature(OVERHEAT_TEMPERATURE);
    }
}

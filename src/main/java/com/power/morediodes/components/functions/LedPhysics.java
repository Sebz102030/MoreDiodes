package com.power.morediodes.components.functions;

import org.patryk3211.powergrid.circuits.thermal.ThermalBuilder;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;

/**
 * Shared LED model and burnout calibration of the 7-segment displays and both LED matrices.
 *
 * Old behaviour: the thermal unit was sized so the RATED power heated the part to 85 C while it
 * burns at 90 C, assuming a 22 C ambient. Power Grid's real ambient is biome dependent
 * (13.65 * biomeTemperature + 7.1, about 34 C in a desert), so in a warm biome the part reached
 * 90 C at or even below its rated voltage. In addition the rated voltage was both the voltage the
 * LED lights at and the voltage it burns at, so there was no headroom at all.
 *
 * Now the burn point is calibrated from the CONFIGURED MAX VOLTAGE: the thermal unit reaches the
 * overheat temperature only when the power at (max voltage x BURN_TOLERANCE) is dissipated, even in
 * the hottest ambient we design for. So a part never burns at or below its configured max voltage,
 * and burns a little above it (the exact voltage moves up slightly in cooler biomes).
 */
public final class LedPhysics {

    public static final double SERIES_R = 4.0D;
    public static final double TEMP_C = 22.0D;
    public static final double IDEALITY = 2.0D;
    public static final double BREAKDOWN_V = 1000.0D;
    public static final double BREAKDOWN_IS = 1.0e-6D;

    public static final float THERMAL_MASS = 0.005f;
    public static final float OVERHEAT_TEMP = 90f;
    /** Hottest ambient (C) at which a part must still survive its max voltage (desert / nether are ~34 C). */
    public static final float DESIGN_AMBIENT = 36f;
    /** Burn point sits this much above the configured max voltage, so exactly max voltage is safe. */
    public static final double BURN_TOLERANCE = 1.03D;

    private static final double BOLTZMANN_OVER_Q = 8.617333e-5D;

    private LedPhysics() {
    }

    /** Reverse saturation current so the LED (junction + series resistance) passes forwardCurrent at forwardVoltage. */
    public static double saturationCurrent(double forwardVoltage, double forwardCurrent) {
        double junctionVoltage = Math.max(forwardVoltage - forwardCurrent * SERIES_R, 0.2D * forwardVoltage);
        return forwardCurrent / Math.expm1(junctionVoltage / thermalVoltage());
    }

    /** Current through the LED (junction + series resistance) when terminalVoltage is across it. */
    public static double currentAt(double terminalVoltage, double saturationCurrent) {
        if (terminalVoltage <= 0)
            return 0;
        double nVt = thermalVoltage();
        double lo = 0, hi = terminalVoltage / SERIES_R;
        for (int i = 0; i < 100; i++) {
            double mid = 0.5D * (lo + hi);
            double f = saturationCurrent * Math.expm1((terminalVoltage - mid * SERIES_R) / nVt) - mid;
            if (f > 0)
                lo = mid;
            else
                hi = mid;
        }
        return 0.5D * (lo + hi);
    }

    /** Power (W) the LED dissipates at which it must burn: the power at the configured max voltage. */
    public static float burnPower(double forwardVoltage, double forwardCurrent, double maxVoltage) {
        double is = saturationCurrent(forwardVoltage, forwardCurrent);
        double burnVoltage = Math.max(maxVoltage, forwardVoltage) * BURN_TOLERANCE;
        return (float) (burnVoltage * currentAt(burnVoltage, is));
    }

    /** Power (W) of a plain resistor at the configured max voltage (for the matrix VCC-GND load). */
    public static float resistorBurnPower(double maxVoltage, double ohms) {
        double v = maxVoltage * BURN_TOLERANCE;
        return (float) (v * v / ohms);
    }

    /**
     * Thermal setup: the unit reaches OVERHEAT_TEMP exactly when burnPower is dissipated at
     * DESIGN_AMBIENT. (setMaxPower computes dissipation = power / (temperature - 22 C).)
     */
    public static ThermalBuilder burnAt(ThermalBuilder builder, float burnPower) {
        return builder
                .setMaxPower(burnPower, ThermalBehaviour.STANDARD_TEMPERATURE + (OVERHEAT_TEMP - DESIGN_AMBIENT))
                .setOverheatTemperature(OVERHEAT_TEMP)
                .setThermalMass(THERMAL_MASS);
    }

    /**
     * Adds a thermal unit with no heat source that can never overheat. Call it LAST in bake().
     *
     * Why: when a board is loaded, Power Grid goes over the thermal units of a component in order and
     * sets "destroyed" (the component is then drawn as wrecked / not drawn at all) from the state of
     * each unit, so the LAST unit decides. A display whose last unit (the 7-segment DP, the last
     * matrix diode) had burnt came back from a relog marked as destroyed and disappeared. With this
     * unit last, a burnt diode never decides it. It does not count in RepairSupport (unitCount).
     */
    public static void addSentinelUnit(ThermalBuilder.IEmitter thermals) {
        thermals.builder()
                .setThermalMass(THERMAL_MASS)
                .setOverheatTemperature(1.0e9f);
    }

    private static double thermalVoltage() {
        return IDEALITY * BOLTZMANN_OVER_Q * (TEMP_C + 273.15D);
    }
}

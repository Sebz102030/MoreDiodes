package com.power.morediodes.registry;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ConfigFile {

    public static final ModConfigSpec SPEC;

    // --- 7-segment displays ---
    public static final ModConfigSpec.DoubleValue SEGMENT_ACTIVATION_VOLTAGE;
    public static final ModConfigSpec.DoubleValue SEGMENT_VOLTAGE;
    public static final ModConfigSpec.DoubleValue SEGMENT_CURRENT_MA;

    // --- 5x5 LED matrix ---
    public static final ModConfigSpec.DoubleValue MATRIX_DIODE_ACTIVATION_VOLTAGE;
    public static final ModConfigSpec.DoubleValue MATRIX_DIODE_VOLTAGE;
    public static final ModConfigSpec.DoubleValue MATRIX_DIODE_CURRENT_MA;
    public static final ModConfigSpec.DoubleValue MATRIX_MAX_SUPPLY_VOLTAGE;

    // --- LED bargraph (10 LED bar) ---
    public static final ModConfigSpec.DoubleValue BAR_DIODE_ACTIVATION_VOLTAGE;
    public static final ModConfigSpec.DoubleValue BAR_DIODE_VOLTAGE;
    public static final ModConfigSpec.DoubleValue BAR_DIODE_CURRENT_MA;

    // --- 4-bit to 7-segment decoder ---
    public static final ModConfigSpec.DoubleValue DECODER_OUTPUT_CURRENT_MA;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("7-segment display settings")
                .push("sevenSegmentDisplay");

        SEGMENT_ACTIVATION_VOLTAGE = builder
                .comment("Activation voltage (V) of a segment: the voltage across it at its rated current,",
                        "so the segment starts lighting around this voltage.",
                        " Default: 1.0",
                        " Range: 0.5 ~ 12.0")
                .defineInRange("segmentActivationVoltage", 1.0D, 0.5D, 12.0D);

        SEGMENT_VOLTAGE = builder
                .comment("", "MAX voltage (V) across a segment. A segment never burns at or below this voltage and burns",
                        "just above it (through Power Grid's thermal simulation).",
                        "Should be at or above segmentActivationVoltage (it is raised to it if lower).",
                        " Default: 3.0",
                        " Range: 0.5 ~ 12.0")
                .defineInRange("segmentVoltage", 3.0D, 0.5D, 12.0D);

        SEGMENT_CURRENT_MA = builder
                .comment("", "Current (mA) drawn by every lit segment at the activation voltage.",
                        " Default: 20.0",
                        " Range: 1.0 ~ 500.0")
                .defineInRange("segmentCurrentMilliamps", 20.0D, 1.0D, 500.0D);

        builder.pop();

        builder.comment("", "5x5 LED matrix settings")
                .push("ledMatrix");

        MATRIX_DIODE_ACTIVATION_VOLTAGE = builder
                .comment("", "Activation voltage (V) of a matrix diode: the voltage across it at its rated current,",
                        "so the diode starts lighting around this voltage. For BOTH LED matrices.",
                        "The 13-pin matrix needs the same voltage between VCC and GND to work.",
                        " Default: 1.0",
                        " Range: 0.5 ~ 12.0")
                .defineInRange("matrixDiodeActivationVoltage", 1.0D, 0.5D, 12.0D);

        MATRIX_DIODE_VOLTAGE = builder
                .comment("", "MAX voltage (V) across a matrix diode, for BOTH LED matrices. A diode never burns at or",
                        "below this voltage and burns just above it (through Power Grid's thermal simulation).",
                        "Should be at or above matrixDiodeActivationVoltage (it is raised to it if lower).",
                        " Default: 3.0",
                        " Range: 0.5 ~ 12.0")
                .defineInRange("matrixDiodeVoltage", 3.0D, 0.5D, 12.0D);

        MATRIX_DIODE_CURRENT_MA = builder
                .comment("", "Current (mA) drawn by a conducting matrix diode at the diode voltage.",
                        " Default: 20.0",
                        " Range: 1.0 ~ 500.0")
                .defineInRange("matrixDiodeCurrentMilliamps", 20.0D, 1.0D, 500.0D);

        MATRIX_MAX_SUPPLY_VOLTAGE = builder
                .comment("", "Maximum voltage (V) between VCC and GND. The whole matrix never burns at or below this",
                        "voltage and burns just above it. The supply needs the diode activation voltage to work.",
                        " Default: 6.0",
                        " Range: 1.0 ~ 100.0")
                .defineInRange("matrixMaxSupplyVoltage", 6.0D, 1.0D, 100.0D);

        builder.pop();
        builder.comment("", "LED bargraph (10 LED bar) settings")
                .push("ledBargraph");

        BAR_DIODE_ACTIVATION_VOLTAGE = builder
                .comment("Activation voltage (V) of a bar LED: the voltage across it at its rated current,",
                        "so the LED starts lighting around this voltage.",
                        " Default: 1.0",
                        " Range: 0.5 ~ 12.0")
                .defineInRange("barDiodeActivationVoltage", 1.0D, 0.5D, 12.0D);

        BAR_DIODE_VOLTAGE = builder
                .comment("", "MAX voltage (V) across a bar LED. An LED never burns at or below this voltage and burns",
                        "just above it (through Power Grid's thermal simulation).",
                        "Should be at or above barDiodeActivationVoltage (it is raised to it if lower).",
                        " Default: 3.0",
                        " Range: 0.5 ~ 12.0")
                .defineInRange("barDiodeVoltage", 3.0D, 0.5D, 12.0D);

        BAR_DIODE_CURRENT_MA = builder
                .comment("", "Current (mA) drawn by a lit bar LED at the activation voltage.",
                        " Default: 20.0",
                        " Range: 1.0 ~ 500.0")
                .defineInRange("barDiodeCurrentMilliamps", 20.0D, 1.0D, 500.0D);

        builder.pop();
        builder.comment("", "4-bit to 7-segment decoder settings")
                .push("sevenSegmentDecoder");

        DECODER_OUTPUT_CURRENT_MA = builder
                .comment("Current (mA) every lit output of the decoder aims to deliver into a 7-segment display.",
                        "The output resistance is set from the supply voltage and segmentActivationVoltage,",
                        "so a segment gets about this current.",
                        " Default: 20.0",
                        " Range: 1.0 ~ 100.0")
                .defineInRange("decoderOutputCurrentMilliamps", 20.0D, 1.0D, 100.0D);

        builder.pop();
        SPEC = builder.build();
    }
    private ConfigFile() {
    }
}
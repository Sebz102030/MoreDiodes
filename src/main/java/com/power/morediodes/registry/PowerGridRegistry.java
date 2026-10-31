package com.power.morediodes.registry;

import com.power.morediodes.components.ComponentDiodeWhite;
import com.power.morediodes.components.ComponentZenerDiode;
import com.power.morediodes.components.SevenSegmentDisplayComponent;
import com.power.morediodes.components.LedBargraphComponent;
import com.power.morediodes.components.SimpleLedMatrixComponent;
import com.power.morediodes.components.LedMatrixComponent;
import com.power.morediodes.components.SevenSegmentDecoderComponent;
import com.power.morediodes.MoreDiodes;

import org.patryk3211.powergrid.circuits.schematic.ComponentFootprint;
import org.patryk3211.powergrid.circuits.components.ComponentRegistry;

import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = MoreDiodes.MODID)
class PowerGridRegistry {
    public static final ResourceLocation WHITE_DIODE_ID = id("diode_white");
    public static final ResourceLocation BLUE_DIODE_ID = id("diode_blue");
    public static final ResourceLocation YELLOW_DIODE_ID = id("diode_yellow");
    public static final ResourceLocation RED_DIODE_ID = id("diode_red");
    public static final ResourceLocation GREEN_DIODE_ID = id("diode_green");
    public static final ResourceLocation ZENER_DIODE_ID = id("diode_zener");

    public static final ResourceLocation LED_MATRIX_ID = id("led_matrix");
    public static final ResourceLocation LED_MATRIX_ADVANCED_ID = id("led_matrix_advanced");
    public static final ResourceLocation LED_BARGRAPH_ID = id("led_bargraph");
    public static final ResourceLocation SEVEN_SEGMENT_ANODE_ID = id("seven_segment_anode");
    public static final ResourceLocation SEVEN_SEGMENT_CATHODE_ID = id("seven_segment_cathode");

    public static final ResourceLocation SEVEN_SEGMENT_DECODER_CATHODE_ID = id("seven_segment_decoder_cathode");
    public static final ResourceLocation SEVEN_SEGMENT_DECODER_ANODE_ID = id("seven_segment_decoder_anode");


    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MoreDiodes.MODID, path);
    }

    private PowerGridRegistry(){}

    private static ComponentDiodeWhite buildDiodeWhite(ComponentDiodeWhite.DiodeColour color, String name) {
        var footprint = new ComponentFootprint.Builder(2, 2, "component."+MoreDiodes.MODID+".diode_"+name, null)
            .addPad(1, 0, ComponentDiodeWhite.PIN_ANODE, "ANODE", "ANODE")
            .addPad(0, 1, ComponentDiodeWhite.PIN_KATHODE, "KATHODE", "KATHODE")
            .withItem().withOutline().build();
        return new ComponentDiodeWhite(footprint, color);}
    
    private static ComponentZenerDiode buildZener() {
        var footprint = new ComponentFootprint.Builder(3, 1, "component."+MoreDiodes.MODID+".diode_zener", null)
            .addPad(0, 0, ComponentZenerDiode.PIN_ANODE, "ANODE", "ANODE")
            .addPad(2, 0, ComponentZenerDiode.PIN_KATHODE, "KATHODE", "KATHODE")
            .withItem().withOutline().build();
        return new ComponentZenerDiode(footprint);}

    private static ComponentFootprint sevenSegmentFootprint(String idPath) {
        String base = "component." + MoreDiodes.MODID + "." + idPath;
        return new ComponentFootprint.Builder(5, 7, base, null)
                .addPad(0, 0, 6, "G", "g")
                .addPad(1, 0, 5, "F", "f")
                .addPad(2, 0, SevenSegmentDisplayComponent.PIN_COM_WEST, "COM", "COM")
                .addPad(3, 0, 0, "A", "a")
                .addPad(4, 0, 1, "B", "b")
                .addPad(0, 6, 4, "E", "e")
                .addPad(1, 6, 3, "D", "d")
                .addPad(2, 6, SevenSegmentDisplayComponent.PIN_COM_EAST, "COM", "COM")
                .addPad(3, 6, 2, "C", "c")
                .addPad(4, 6, SevenSegmentDisplayComponent.PIN_DP, "DP", "dp")
                .withItem()
                .withOutline()
                .build();
    }

    private static SevenSegmentDisplayComponent buildSevenSegmentDisplayCathode() {
        return new SevenSegmentDisplayComponent(sevenSegmentFootprint("seven_segment_cathode"), false);
    }

    private static SevenSegmentDisplayComponent buildSevenSegmentDisplayAnode() {
        return new SevenSegmentDisplayComponent(sevenSegmentFootprint("seven_segment_anode"), true);
    }

    private static LedMatrixComponent buildLedMatrix() {
        String base = "component." + MoreDiodes.MODID + ".led_matrix_advanced";
        var builder = new ComponentFootprint.Builder(5, 5, base, null);
        for (int i = 0; i < 5; i++) {
            builder.addPad(i, 0, LedMatrixComponent.PIN_CATHODE_0 + i, "C" + (i + 1), "c" + (i + 1));
            builder.addPad(i, 4, LedMatrixComponent.PIN_ANODE_0 + i, "A" + (i + 1), "a" + (i + 1));
        }
        builder.addPad(1, 2, LedMatrixComponent.PIN_VCC, "VCC", "VCC")
                .addPad(2, 2, LedMatrixComponent.PIN_CLEAR, "CLEAR", "CLR")
                .addPad(3, 2, LedMatrixComponent.PIN_GND, "GND", "GND");
        return new LedMatrixComponent(builder.withItem().withOutline().build());
    }

    private static SimpleLedMatrixComponent buildSimpleLedMatrix() {
        String base = "component." + MoreDiodes.MODID + ".led_matrix";
        var builder = new ComponentFootprint.Builder(5, 5, base, null);
        for (int i = 0; i < 5; i++) {
            builder.addPad(i, 0, LedMatrixComponent.PIN_CATHODE_0 + i, "C" + (i + 1), "c" + (i + 1));
            builder.addPad(i, 4, LedMatrixComponent.PIN_ANODE_0 + i, "A" + (i + 1), "a" + (i + 1));
        }
        return new SimpleLedMatrixComponent(builder.withItem().withOutline().build());
    }

    private static LedBargraphComponent buildLedBargraph() {
        String base = "component." + MoreDiodes.MODID + ".led_bargraph";
        var builder = new ComponentFootprint.Builder(LedBargraphComponent.BASE_WIDTH,
                LedBargraphComponent.BASE_HEIGHT, base, null);
        for (int i = 0; i < LedBargraphComponent.LEDS; i++) {
            builder.addPad(3, i, LedBargraphComponent.PIN_ANODE_0 + i, "A" + (i + 1), "a" + (i + 1));
            builder.addPad(0, i, LedBargraphComponent.PIN_CATHODE_0 + i, "C" + (i + 1), "c" + (i + 1));
        }
        return new LedBargraphComponent(builder.withItem().withOutline().build());
    }

    /** 7 x 3: bottom row outputs A..G; top row VCC, GND, gap, D0..D3. */
    private static SevenSegmentDecoderComponent buildSevenSegmentDecoder(String idPath, boolean commonAnode) {
        String base = "component." + MoreDiodes.MODID + "." + idPath;
        var builder = new ComponentFootprint.Builder(SevenSegmentDecoderComponent.BASE_WIDTH,
                SevenSegmentDecoderComponent.BASE_HEIGHT, base, null);
        String names = "ABCDEFG";
        for (int i = 0; i < SevenSegmentDecoderComponent.OUTPUTS; i++)
            builder.addPad(i, 2, SevenSegmentDecoderComponent.PIN_OUT_0 + i, "" + names.charAt(i),
                    "" + Character.toLowerCase(names.charAt(i)));
        builder.addPad(0, 0, SevenSegmentDecoderComponent.PIN_VCC, "VCC", "vcc");
        builder.addPad(1, 0, SevenSegmentDecoderComponent.PIN_GND, "GND", "gnd");
        for (int i = 0; i < SevenSegmentDecoderComponent.INPUTS; i++)
            builder.addPad(3 + i, 0, SevenSegmentDecoderComponent.PIN_IN_0 + i, "D" + i, "d" + i);
        return new SevenSegmentDecoderComponent(builder.withItem().withOutline().build(), commonAnode);
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(ComponentRegistry.REGISTRY_KEY)){
            event.register(ComponentRegistry.REGISTRY_KEY, WHITE_DIODE_ID, () -> buildDiodeWhite(ComponentDiodeWhite.DiodeColour.white, "white"));
            event.register(ComponentRegistry.REGISTRY_KEY, BLUE_DIODE_ID, () -> buildDiodeWhite(ComponentDiodeWhite.DiodeColour.blue, "blue"));
            event.register(ComponentRegistry.REGISTRY_KEY, YELLOW_DIODE_ID, () -> buildDiodeWhite(ComponentDiodeWhite.DiodeColour.yellow, "yellow"));
            event.register(ComponentRegistry.REGISTRY_KEY, RED_DIODE_ID, () -> buildDiodeWhite(ComponentDiodeWhite.DiodeColour.red, "red"));
            event.register(ComponentRegistry.REGISTRY_KEY, GREEN_DIODE_ID, () -> buildDiodeWhite(ComponentDiodeWhite.DiodeColour.green, "green"));
            event.register(ComponentRegistry.REGISTRY_KEY, ZENER_DIODE_ID, PowerGridRegistry::buildZener);

            event.register(ComponentRegistry.REGISTRY_KEY, SEVEN_SEGMENT_CATHODE_ID, PowerGridRegistry::buildSevenSegmentDisplayCathode);
            event.register(ComponentRegistry.REGISTRY_KEY, SEVEN_SEGMENT_ANODE_ID, PowerGridRegistry::buildSevenSegmentDisplayAnode);
            event.register(ComponentRegistry.REGISTRY_KEY, LED_MATRIX_ID, PowerGridRegistry::buildSimpleLedMatrix);
            event.register(ComponentRegistry.REGISTRY_KEY, LED_MATRIX_ADVANCED_ID, PowerGridRegistry::buildLedMatrix);
            event.register(ComponentRegistry.REGISTRY_KEY, LED_BARGRAPH_ID, PowerGridRegistry::buildLedBargraph);

            event.register(ComponentRegistry.REGISTRY_KEY, SEVEN_SEGMENT_DECODER_CATHODE_ID,
                    () -> buildSevenSegmentDecoder("seven_segment_decoder_cathode", false));
            event.register(ComponentRegistry.REGISTRY_KEY, SEVEN_SEGMENT_DECODER_ANODE_ID,
                    () -> buildSevenSegmentDecoder("seven_segment_decoder_anode", true));

            MoreDiodes.LOGGER.info("Registered {} custom powergrid components", 11);
        }
    }
}
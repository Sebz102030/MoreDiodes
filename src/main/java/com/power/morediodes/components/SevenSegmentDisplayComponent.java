package com.power.morediodes.components;

import com.google.common.collect.ImmutableCollection;
import com.mojang.blaze3d.vertex.PoseStack;
import com.power.morediodes.registry.ConfigFile;
import com.power.morediodes.MoreDiodes;
import com.power.morediodes.components.functions.GlowLayout;
import com.power.morediodes.components.functions.LedPhysics;
import com.power.morediodes.components.functions.ModLang;
import com.power.morediodes.components.functions.RepairSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.circuitboard.ComponentCircuitBuilder;
import org.patryk3211.powergrid.circuits.components.IComponentGoggleInformation;
import org.patryk3211.powergrid.circuits.components.IInteractableComponent;
import org.patryk3211.powergrid.circuits.components.IRenderedComponent;
import org.patryk3211.powergrid.circuits.components.OrientableComponent;
import org.patryk3211.powergrid.circuits.components.properties.ComponentProperty;
import org.patryk3211.powergrid.circuits.components.properties.IntProperty;
import org.patryk3211.powergrid.circuits.schematic.ComponentFootprint;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;
import org.patryk3211.powergrid.circuits.thermal.ThermalBuilder;
import org.patryk3211.powergrid.electricity.sim.special.PNJunctionWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * 7-segment display with a decimal point (8 LEDs), 10 pins, modelled on the
 * standard 5161AS / 5161BS packages. One class, two variants (common cathode
 * and common anode) that share one 5 (x) x 7 (y) footprint and one model, and
 * both can be rotated like any other orientable component.
 *
 * Default orientation: 5 wide, 7 tall, digit top toward the north, pins
 * top row, left to right: G F COM A B
 * bottom row, left to right: E D COM C DP
 * The footprint and the baked model are rotated by Power Grid itself; the
 * glowing segment overlay ({@link GlowLayout}, positions in
 * assets/morediodes/glow/seven_segment.json) applies the same rotation.
 *
 * Node indices: 0..6 = segments A..G, 7 = DP, 8 and 9 = the two COM pins,
 * tied together internally.
 *
 * Electrical model: every segment is its own red-LED PN junction (4 ohm series
 * resistance;
 * voltage and current set by the segmentVoltage and segmentCurrentMilliamps
 * config entries,
 * default 2 V and 20 mA). segmentVoltage is also the max voltage: a segment
 * never burns at or
 * below it (see {@link LedPhysics}), and burns just above it. Common cathode:
 * each LED runs from its
 * segment pin to COM, so a segment lights when its pin is driven above COM.
 * Common anode: each LED runs from COM to its segment pin, so a segment
 * lights when its pin is pulled below COM. Either way each segment needs a
 * series resistor - overdriving it burns it.
 *
 * Burnout: every segment has its OWN ThermalBuilder unit. When that unit
 * overheats, PowerGrid removes the segment's LED wire and calls the overheat
 * callback, which only records the segment as burnt. The other seven keep
 * working. Nothing here compares temperatures itself.
 *
 * Repair: sneak + right-click a display that has burnt segments, with empty
 * hands (vanilla skips block interaction when sneaking with an item in hand).
 * Only creative players can repair (all burnt segments at once); in survival a
 * burnt segment stays burnt. Repairing rebuilds the board, but every OTHER
 * thermal unit on
 * it gets its saved temperature restored, so damage to other parts is kept.
 * Rotating the display in the creative board editor also clears the burnt
 * state (see {@link #rotate}).
 *
 * Client sync: the board simulation only runs on the server, so brightness is
 * quantised to 4 levels per segment (2 bits each) and synced through the
 * hidden LEVELS property whenever it changes. Burnt segments are the hidden
 * BURNT bit mask.
 *
 * All per-placement state lives on the {@link PlacedComponent} (properties and
 * customData) - this class instance is shared by every placed display.
 */
public class SevenSegmentDisplayComponent extends OrientableComponent
        implements IRenderedComponent, IComponentGoggleInformation, IInteractableComponent {

    public static final int SEGMENTS = 8;
    public static final int PIN_DP = 7;
    public static final int PIN_COM_WEST = 8;
    public static final int PIN_COM_EAST = 9;
    private static final String[] NAMES = { "A", "B", "C", "D", "E", "F", "G", "DP" };

    /**
     * Fractions of the rated current at which a segment reaches brightness level 1,
     * 2 and 3.
     */
    private static final double[] LEVEL_FRACTION = { 0.05D, 0.40D, 0.75D };
    private static final int[] LEVEL_ALPHA = { 0, 90, 180, 255 };

    private static final float COM_TIE_RESISTANCE = 0.001f;
    public static final IntProperty LEVELS = (IntProperty) new IntProperty(MoreDiodes.MODID, "segment_levels", 0,
            0, 0xFFFF).hidden().unsafe();
    public static final IntProperty BURNT = (IntProperty) new IntProperty(MoreDiodes.MODID, "segment_burnt", 0, 0,
            0xFF).hidden().unsafe();

    /**
     * Unrotated footprint size in board cells (the model is 5 wide in x, 7 long in
     * z).
     */
    private static final int BASE_WIDTH = 5;
    private static final int BASE_HEIGHT = 7;

    private final boolean commonAnode;

    public SevenSegmentDisplayComponent(ComponentFootprint footprint, boolean commonAnode) {
        super(footprint);
        this.commonAnode = commonAnode;
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(LABEL);
        properties.add(LEVELS);
        properties.add(BURNT);
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder,
            ThermalBuilder.@NotNull IEmitter thermals) {
        var com = builder.terminalNode(PIN_COM_WEST);
        // Both COM pins are one electrical node; tie them with a near-zero resistance.
        builder.connect(COM_TIE_RESISTANCE, com, builder.terminalNode(PIN_COM_EAST));

        // Operating point from the config: segmentVoltage across the segment at
        // segmentCurrentMilliamps.
        // segmentVoltage is also the MAX voltage: the segment never burns at or below
        // it.
        double ratedVoltage = ConfigFile.SEGMENT_VOLTAGE.get();
        double ratedCurrent = ConfigFile.SEGMENT_CURRENT_MA.get() / 1000.0D;
        double saturationCurrent = LedPhysics.saturationCurrent(ratedVoltage, ratedCurrent);
        float burnPower = LedPhysics.burnPower(ratedVoltage, ratedCurrent, ratedVoltage);

        int burnt = placed.get(BURNT);
        PNJunctionWire[] leds = new PNJunctionWire[SEGMENTS];
        for (int i = 0; i < SEGMENTS; i++) {
            final int segment = i;
            // A thermal unit is always requested for every segment, in order, so each keeps
            // a
            // stable index (its saved temperature is keyed by it) even when others are
            // burnt.
            var thermal = LedPhysics.burnAt(thermals.builder(), burnPower)
                    .withOverheatCallback(() -> burn(placed, segment));
            if ((burnt & (1 << i)) != 0)
                continue; // already dead: no LED in the circuit

            // PNJunctionWire(node1 = anode, node2 = cathode)
            var segmentNode = builder.terminalNode(i);
            var led = commonAnode
                    ? new PNJunctionWire(saturationCurrent, LedPhysics.SERIES_R, LedPhysics.TEMP_C,
                            LedPhysics.IDEALITY, LedPhysics.BREAKDOWN_V, LedPhysics.BREAKDOWN_IS, com, segmentNode)
                    : new PNJunctionWire(saturationCurrent, LedPhysics.SERIES_R, LedPhysics.TEMP_C,
                            LedPhysics.IDEALITY, LedPhysics.BREAKDOWN_V, LedPhysics.BREAKDOWN_IS, segmentNode, com);
            builder.add(led);
            leds[i] = led;
            thermal.addHeatSource(led);
        }
        LedPhysics.addSentinelUnit(thermals); // keep last: see LedPhysics.addSentinelUnit
        placed.customData = new Runtime(leds, ratedCurrent);
    }

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        if (placed.isClient())
            return false;
        if (!(placed.customData instanceof Runtime rt))
            return true;

        int burnt = placed.get(BURNT);
        int packed = 0;
        for (int i = 0; i < SEGMENTS; i++) {
            if ((burnt & (1 << i)) != 0 || rt.leds[i] == null)
                continue;
            packed |= levelFor(rt.leds[i].current(), rt.ratedCurrent) << (2 * i);
        }
        if (packed != placed.get(LEVELS)) {
            placed.set(LEVELS, packed);
            placed.notifyClients(LEVELS);
        }
        return true;
    }

    private static int levelFor(double current, double ratedCurrent) {
        int level = 0;
        for (double fraction : LEVEL_FRACTION) {
            if (current >= fraction * ratedCurrent)
                level++;
        }
        return level;
    }

    /**
     * ThermalBuilder overheat callback for one segment. PowerGrid has already
     * removed that segment's LED wire; this only records it so it stays dead
     * after a reload and the other clients/renderers know.
     */
    private static void burn(@NotNull PlacedComponent placed, int segment) {
        if (placed.isClient())
            return;
        int burnt = placed.get(BURNT);
        if ((burnt & (1 << segment)) != 0)
            return;
        placed.set(BURNT, burnt | (1 << segment));
        placed.notifyClients(BURNT);
        // Make sure a dead segment shows dark immediately.
        placed.set(LEVELS, placed.get(LEVELS) & ~(3 << (2 * segment)));
        placed.notifyClients(LEVELS);
    }

    /**
     * Rotating in the creative board editor also restores the display: the
     * editor saves the schematic back to the board, which rebuilds it with
     * every thermal unit starting cold, so the burnt state has to go too.
     */
    @Override
    public boolean rotate(@NotNull PlacedComponent placed, boolean counterClockwise) {
        boolean rotated = super.rotate(placed, counterClockwise);
        if (rotated) {
            placed.set(BURNT, 0);
            placed.set(LEVELS, 0);
        }
        return rotated;
    }

    /**
     * Only a display with burnt segments is interactable. Otherwise the shape is a
     * 1/1000-block speck at the footprint corner, so clicks on a healthy display
     * still
     * reach the board's own handling (the creative board editor).
     */
    @Override
    public VoxelShape getShape(@NotNull PlacedComponent placed) {
        if (placed.get(BURNT) == 0) {
            float x = placed.x / 16f, z = placed.y / 16f;
            return Shapes.box(x, BASE_Y, z, x + 0.001f, BASE_Y + 0.001f, z + 0.001f);
        }
        return IInteractableComponent.extrudedFootprint(placed, 3 / 16f);
    }

    @Override
    public InteractionResult use(CircuitBoardBlockEntity be, PlacedComponent placed, Player player) {
        int burnt = placed.get(BURNT);
        if (burnt == 0)
            return InteractionResult.PASS;
        if (player.level().isClientSide)
            return InteractionResult.SUCCESS;

        if (!player.isCreative()) {
            player.displayClientMessage(ModLang.component("message.morediodes.segments_burnt", burntList(burnt))
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        if (!player.isShiftKeyDown()) {
            player.displayClientMessage(ModLang.component("message.morediodes.segments_burnt_repair", burntList(burnt))
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }

        repair(be, placed, burnt);
        player.displayClientMessage(ModLang.component("message.morediodes.segments_repaired", burntList(burnt))
                .withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.SUCCESS;
    }

    /**
     * Clears the burnt bits in {@code mask} and rebuilds the board so those LEDs
     * rejoin the
     * circuit; heat of every other thermal unit on the board is kept (see
     * {@link RepairSupport#rebuild}).
     */
    private static void repair(CircuitBoardBlockEntity be, PlacedComponent placed, int mask) {
        RepairSupport.rebuild(be, placed, SEGMENTS, index -> (mask & (1 << index)) != 0, () -> {
            placed.set(BURNT, placed.get(BURNT) & ~mask);
            placed.set(LEVELS, 0);
        });
    }

    /** Translatable name; a Component (not a String) so a server-sent message is translated by the client. */
    private static MutableComponent segmentName(int segment) {
        if (segment == PIN_DP)
            return ModLang.component("tooltip.morediodes.decimal_point");
        return ModLang.component("tooltip.morediodes.segment", NAMES[segment]);
    }

    private static MutableComponent burntList(int burnt) {
        MutableComponent list = Component.empty();
        boolean first = true;
        for (int i = 0; i < SEGMENTS; i++) {
            if ((burnt & (1 << i)) == 0)
                continue;
            if (!first)
                list.append(", ");
            list.append(segmentName(i));
            first = false;
        }
        return list;
    }

    @Override
    public void render(CircuitBoardBlockEntity be, PlacedComponent placed, float partialTicks, PoseStack ms,
            MultiBufferSource bufferSource, int light, int overlay) {
        int levels = placed.get(LEVELS);
        if (levels == 0)
            return;

        // Where each segment glows comes from assets/morediodes/glow/seven_segment.json (resource-pack editable).
        GlowLayout.get(GlowLayout.SEVEN_SEGMENT).render(ms, bufferSource, placed.get(ORIENTATION),
                BASE_WIDTH, BASE_HEIGHT, true, 255, 45, 30,
                i -> i < SEGMENTS ? LEVEL_ALPHA[(levels >> (2 * i)) & 3] : 0);
    }

    @Override
    public boolean addToGoggleTooltip(@NotNull PlacedComponent placed, @NotNull List<Component> tooltip,
            boolean isPlayerSneaking) {
        int burnt = placed.get(BURNT);
        ComponentLabels.addToTooltip(placed, tooltip);
        ModLang.text(commonAnode ? "item.morediodes.seven_segment_anode" : "item.morediodes.seven_segment_cathode")
                .forGoggles(tooltip);
        if (burnt != 0) {
            for (int i = 0; i < SEGMENTS; i++) {
                if ((burnt & (1 << i)) != 0)
                    ModLang.text("tooltip.morediodes.segment_burnt", segmentName(i))
                            .style(ChatFormatting.RED).forGoggles(tooltip);
            }
            if (ModLang.localPlayerIsCreative())
                ModLang.text("tooltip.morediodes.repair").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip);
        }
        return true;
    }

    /**
     * Per-placement live LED wires (null entries are burnt segments), stashed via
     * {@link PlacedComponent#customData}.
     */
    private record Runtime(PNJunctionWire[] leds, double ratedCurrent) {
    }
}

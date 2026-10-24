package com.power.morediodes.components;

import com.google.common.collect.ImmutableCollection;
import com.mojang.blaze3d.vertex.PoseStack;
import com.power.morediodes.MoreDiodes;
import com.power.morediodes.components.functions.GlowLayout;
import com.power.morediodes.components.functions.LedPhysics;
import com.power.morediodes.components.functions.ModLang;
import com.power.morediodes.components.functions.RepairSupport;
import com.power.morediodes.registry.ConfigFile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
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
import org.patryk3211.powergrid.circuits.components.properties.EnumProperty;
import org.patryk3211.powergrid.circuits.components.properties.IntProperty;
import org.patryk3211.powergrid.circuits.schematic.ComponentFootprint;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;
import org.patryk3211.powergrid.circuits.thermal.ThermalBuilder;
import org.patryk3211.powergrid.electricity.sim.special.PNJunctionWire;

import java.util.List;

/**
 * LED bargraph: a 10 LED bar graph with 20 pins (4 x 10 footprint, standing north-south), every LED independent.
 *
 * Default orientation: east column (x=3) anodes A1..A10, west column (x=0) cathodes C1..C10, top to bottom.
 * LED n lights between anode n and cathode n. Node indices: 0..9 anodes, 10..19 cathodes.
 *
 * Every LED is its own red-LED style PN junction with its own thermal unit: it lights from the activation
 * voltage (barDiodeActivationVoltage config, default 1 V) and never burns at or below barDiodeVoltage; more voltage across
 * one LED burns just that LED. Brightness is quantised to 4 levels per LED and synced through the hidden
 * LEVELS property; burnt LEDs are the hidden BURNT bit mask. Creative players repair by sneak + right-click.
 * Colour is picked in the board editor like the Power Grid bulb (default red).
 *
 * Where each LED glows is NOT in the code: assets/morediodes/glow/led_bargraph.json (resource-pack editable).
 */
public class LedBargraphComponent extends OrientableComponent
        implements IRenderedComponent, IComponentGoggleInformation, IInteractableComponent {

    public static final int LEDS = 10;
    public static final int ALL = (1 << LEDS) - 1;
    public static final int PIN_ANODE_0 = 0;
    public static final int PIN_CATHODE_0 = LEDS;

    public static final int BASE_WIDTH = 4;
    public static final int BASE_HEIGHT = 10;

    /** Multiplier from the dye colour to the emissive LED colour (same as the matrices). */
    private static final float LED_BRIGHTEN = 1.45f;

    /** Fractions of the rated current at which an LED reaches brightness level 1, 2 and 3. */
    private static final double[] LEVEL_FRACTION = { 0.05D, 0.40D, 0.75D };
    private static final int[] LEVEL_ALPHA = { 0, 90, 180, 255 };

    /** The same colour setting the LED matrices use (one colour for the whole component, default red). */
    public static final EnumProperty<DyeColor> COLOR = LedMatrixComponent.COLOR;

    public static final IntProperty LEVELS = (IntProperty) new IntProperty(MoreDiodes.MODID, "bar_levels", 0,
            0, (1 << (2 * LEDS)) - 1).hidden().unsafe();
    public static final IntProperty BURNT = (IntProperty) new IntProperty(MoreDiodes.MODID, "bar_burnt", 0,
            0, ALL).hidden().unsafe();

    public LedBargraphComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(LABEL);
        properties.add(COLOR);
        properties.add(LEVELS);
        properties.add(BURNT);
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder,
            ThermalBuilder.@NotNull IEmitter thermals) {
        double activationVoltage = ConfigFile.BAR_DIODE_ACTIVATION_VOLTAGE.get();
        double maxVoltage = Math.max(ConfigFile.BAR_DIODE_VOLTAGE.get(), activationVoltage);
        double ratedCurrent = ConfigFile.BAR_DIODE_CURRENT_MA.get() / 1000.0D;
        double saturationCurrent = LedPhysics.saturationCurrent(activationVoltage, ratedCurrent);
        float burnPower = LedPhysics.burnPower(activationVoltage, ratedCurrent, maxVoltage);

        int burnt = placed.get(BURNT);
        PNJunctionWire[] leds = new PNJunctionWire[LEDS];
        for (int i = 0; i < LEDS; i++) {
            final int led = i;
            // Always request a thermal unit, in order, so saved temperatures keep a stable index.
            var thermal = LedPhysics.burnAt(thermals.builder(), burnPower)
                    .withOverheatCallback(() -> burn(placed, led));
            if ((burnt & (1 << i)) != 0)
                continue;
            // PNJunctionWire(node1 = anode, node2 = cathode)
            var wire = new PNJunctionWire(saturationCurrent, LedPhysics.SERIES_R, LedPhysics.TEMP_C,
                    LedPhysics.IDEALITY, LedPhysics.BREAKDOWN_V, LedPhysics.BREAKDOWN_IS,
                    builder.terminalNode(PIN_ANODE_0 + i), builder.terminalNode(PIN_CATHODE_0 + i));
            builder.add(wire);
            leds[i] = wire;
            thermal.addHeatSource(wire);
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
        for (int i = 0; i < LEDS; i++) {
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

    /** Overheat callback: PowerGrid has already removed the LED wire; only record it. */
    private static void burn(@NotNull PlacedComponent placed, int led) {
        if (placed.isClient())
            return;
        int burnt = placed.get(BURNT);
        if ((burnt & (1 << led)) != 0)
            return;
        placed.set(BURNT, burnt | (1 << led));
        placed.notifyClients(BURNT);
        placed.set(LEVELS, placed.get(LEVELS) & ~(3 << (2 * led)));
        placed.notifyClients(LEVELS);
    }

    /** Rotating in the creative board editor rebuilds the board cold, so burnt state goes too. */
    @Override
    public boolean rotate(@NotNull PlacedComponent placed, boolean counterClockwise) {
        boolean rotated = super.rotate(placed, counterClockwise);
        if (rotated) {
            placed.set(BURNT, 0);
            placed.set(LEVELS, 0);
        }
        return rotated;
    }

    /** Only a damaged indicator is interactable (creative repair); otherwise a speck at the footprint corner. */
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

        int count = Integer.bitCount(burnt);
        if (!player.isCreative()) {
            player.displayClientMessage(ModLang.component("message.morediodes.diodes_burnt", count, LEDS)
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        if (!player.isShiftKeyDown()) {
            player.displayClientMessage(ModLang.component("message.morediodes.diodes_burnt_repair", count, LEDS)
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        RepairSupport.rebuild(be, placed, LEDS, index -> (burnt & (1 << index)) != 0, () -> {
            placed.set(BURNT, 0);
            placed.set(LEVELS, 0);
        });
        player.displayClientMessage(ModLang.component(count == 1 ? "message.morediodes.diode_repaired"
                : "message.morediodes.diodes_repaired", count).withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void render(CircuitBoardBlockEntity be, PlacedComponent placed, float partialTicks, PoseStack ms,
            MultiBufferSource bufferSource, int light, int overlay) {
        int levels = placed.get(LEVELS);
        if (levels == 0)
            return;

        int dye = placed.get(COLOR).getTextureDiffuseColor();
        int r = Mth.clamp((int) (((dye >> 16) & 0xFF) * LED_BRIGHTEN), 0, 255);
        int g = Mth.clamp((int) (((dye >> 8) & 0xFF) * LED_BRIGHTEN), 0, 255);
        int b = Mth.clamp((int) ((dye & 0xFF) * LED_BRIGHTEN), 0, 255);

        GlowLayout.get(GlowLayout.LED_BARGRAPH).render(ms, bufferSource, placed.get(ORIENTATION),
                BASE_WIDTH, BASE_HEIGHT, false, r, g, b,
                i -> i < LEDS ? LEVEL_ALPHA[(levels >> (2 * i)) & 3] : 0);
    }

    @Override
    public boolean addToGoggleTooltip(@NotNull PlacedComponent placed, @NotNull List<Component> tooltip,
            boolean isPlayerSneaking) {
        int damaged = Integer.bitCount(placed.get(BURNT));
        ComponentLabels.addToTooltip(placed, tooltip);
        ModLang.text("item.morediodes.led_bargraph").forGoggles(tooltip);
        ModLang.text("tooltip.morediodes.damaged_diodes", damaged, LEDS)
                .style(damaged == 0 ? ChatFormatting.GREEN : ChatFormatting.RED).forGoggles(tooltip);
        if (damaged != 0 && ModLang.localPlayerIsCreative())
            ModLang.text("tooltip.morediodes.repair").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip);
        return true;
    }

    /** Per-placement live LED wires (null entries are burnt LEDs), stashed via {@link PlacedComponent#customData}. */
    private record Runtime(PNJunctionWire[] leds, double ratedCurrent) {
    }
}

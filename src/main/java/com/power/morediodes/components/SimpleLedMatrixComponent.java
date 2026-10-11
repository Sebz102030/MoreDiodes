package com.power.morediodes.components;

import com.google.common.collect.ImmutableCollection;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.power.morediodes.components.functions.ModLang;
import com.power.morediodes.registry.ConfigFile;
import org.jetbrains.annotations.NotNull;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.circuitboard.ComponentCircuitBuilder;
import org.patryk3211.powergrid.circuits.components.IComponentGoggleInformation;
import org.patryk3211.powergrid.circuits.components.IInteractableComponent;
import org.patryk3211.powergrid.circuits.components.IRenderedComponent;
import org.patryk3211.powergrid.circuits.components.OrientableComponent;
import org.patryk3211.powergrid.circuits.components.properties.ComponentProperty;
import org.patryk3211.powergrid.circuits.schematic.ComponentFootprint;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;
import org.patryk3211.powergrid.circuits.thermal.ThermalBuilder;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.PNJunctionWire;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

import static com.power.morediodes.components.LedMatrixComponent.*;
import com.power.morediodes.components.functions.LedPhysics;
import com.power.morediodes.components.functions.RepairSupport;

/**
 * Plain 5x5 LED matrix, 10 pins, no memory and no supply pins.
 *
 * Top row (y=0): cathodes C1..C5 = columns. Bottom row (y=4): anodes A1..A5 =
 * rows.
 * Node indices: 0..4 cathodes, 5..9 anodes. The diode at (row r, column c)
 * lights while it conducts
 * (anode r high, cathode c low); it is powered only by those pins, a loose pin
 * or open switch
 * lights nothing. Same diode config as the other matrix: lights from the
 * activation voltage (2 V by
 * default) and burns only above the max voltage (5 V by default). Goggles show
 * the damaged diode count; creative players repair by
 * sneak + right-click.
 *
 * STATE = lit cells (derived on the server each tick and synced), BURNT = burnt
 * diodes; bit = row * 5 + column.
 */
public class SimpleLedMatrixComponent extends OrientableComponent
        implements IRenderedComponent, IComponentGoggleInformation, IInteractableComponent {

    /** A cell is lit above this fraction of the rated diode current. */
    private static final double LIT_FRACTION = 0.10D;

    public SimpleLedMatrixComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(LABEL);
        properties.add(LedMatrixComponent.COLOR);
        properties.add(STATE);
        properties.add(BURNT);
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder,
            ThermalBuilder.@NotNull IEmitter thermals) {
        FloatingNode[] cathodes = new FloatingNode[SIZE];
        FloatingNode[] anodes = new FloatingNode[SIZE];
        for (int i = 0; i < SIZE; i++) {
            cathodes[i] = builder.terminalNode(PIN_CATHODE_0 + i);
            anodes[i] = builder.terminalNode(PIN_ANODE_0 + i);
        }

        // Diodes light from the activation voltage (rated current there) and burn above
        // the max voltage.
        double activationVoltage = ConfigFile.MATRIX_DIODE_ACTIVATION_VOLTAGE.get();
        double maxVoltage = Math.max(ConfigFile.MATRIX_DIODE_VOLTAGE.get(), activationVoltage);
        double ratedCurrent = ConfigFile.MATRIX_DIODE_CURRENT_MA.get() / 1000.0D;
        double saturationCurrent = LedPhysics.saturationCurrent(activationVoltage, ratedCurrent);
        float burnPower = LedPhysics.burnPower(activationVoltage, ratedCurrent, maxVoltage);

        int burnt = placed.get(BURNT);
        PNJunctionWire[] leds = new PNJunctionWire[DIODES];
        for (int i = 0; i < DIODES; i++) {
            final int diode = i;
            var thermal = LedPhysics.burnAt(thermals.builder(), burnPower)
                    .withOverheatCallback(() -> burn(placed, 1 << diode));
            if ((burnt & (1 << i)) != 0)
                continue;
            var led = new PNJunctionWire(saturationCurrent, LedPhysics.SERIES_R, LedPhysics.TEMP_C,
                    LedPhysics.IDEALITY, LedPhysics.BREAKDOWN_V, LedPhysics.BREAKDOWN_IS,
                    anodes[i / SIZE], cathodes[i % SIZE]);
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
        int lit = 0;
        for (int i = 0; i < DIODES; i++) {
            if ((burnt & (1 << i)) == 0 && rt.leds[i] != null
                    && rt.leds[i].current() >= LIT_FRACTION * rt.ratedCurrent)
                lit |= 1 << i;
        }
        if (lit != placed.get(STATE)) {
            placed.set(STATE, lit);
            placed.notifyClients(STATE);
        }
        return true;
    }

    private static void burn(@NotNull PlacedComponent placed, int mask) {
        if (placed.isClient())
            return;
        int burnt = placed.get(BURNT);
        if ((burnt | mask) == burnt)
            return;
        burnt |= mask;
        placed.set(BURNT, burnt);
        placed.notifyClients(BURNT);
        placed.set(STATE, placed.get(STATE) & ~burnt);
        placed.notifyClients(STATE);
    }

    @Override
    public boolean rotate(@NotNull PlacedComponent placed, boolean counterClockwise) {
        boolean rotated = super.rotate(placed, counterClockwise);
        if (rotated) {
            placed.set(BURNT, 0);
            placed.set(STATE, 0);
        }
        return rotated;
    }

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
            player.displayClientMessage(ModLang.component("message.morediodes.diodes_burnt", count, DIODES)
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        if (!player.isShiftKeyDown()) {
            player.displayClientMessage(ModLang.component("message.morediodes.diodes_burnt_repair", count, DIODES).withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        RepairSupport.rebuild(be, placed, DIODES, index -> (burnt & (1 << index)) != 0, () -> {
            placed.set(BURNT, 0);
            placed.set(STATE, 0);
        });
        player.displayClientMessage(ModLang.component(count == 1 ? "message.morediodes.diode_repaired" : "message.morediodes.diodes_repaired", count)
                .withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void render(CircuitBoardBlockEntity be, PlacedComponent placed, float partialTicks, PoseStack ms,
            MultiBufferSource bufferSource, int light, int overlay) {
        int state = placed.get(STATE);
        if (state != 0)
            LedMatrixComponent.renderCells(ms, bufferSource, placed.get(ORIENTATION), state,
                    placed.get(LedMatrixComponent.COLOR));
    }

    @Override
    public boolean addToGoggleTooltip(@NotNull PlacedComponent placed, @NotNull List<Component> tooltip,
            boolean isPlayerSneaking) {
        int damaged = Integer.bitCount(placed.get(BURNT));
        ComponentLabels.addToTooltip(placed, tooltip);
        ModLang.text("item.morediodes.led_matrix").forGoggles(tooltip);
        ModLang.text("tooltip.morediodes.damaged_diodes", damaged, DIODES)
                .style(damaged == 0 ? ChatFormatting.GREEN : ChatFormatting.RED).forGoggles(tooltip);
        if (damaged != 0)
            if (ModLang.localPlayerIsCreative())
                ModLang.text("tooltip.morediodes.repair").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip);
        return true;
    }

    private static final class Runtime {
        final PNJunctionWire[] leds;
        final double ratedCurrent;

        Runtime(PNJunctionWire[] leds, double ratedCurrent) {
            this.leds = leds;
            this.ratedCurrent = ratedCurrent;
        }
    }
}

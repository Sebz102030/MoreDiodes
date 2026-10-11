package com.power.morediodes.components;

import com.google.common.collect.ImmutableCollection;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.power.morediodes.MoreDiodes;
import com.power.morediodes.components.functions.ModLang;
import com.power.morediodes.registry.ConfigFile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.circuitboard.ComponentCircuitBuilder;
import org.patryk3211.powergrid.circuits.components.IComponentGoggleInformation;
import org.patryk3211.powergrid.circuits.components.IInteractableComponent;
import org.patryk3211.powergrid.circuits.components.IRenderedComponent;
import org.patryk3211.powergrid.circuits.components.OrientableComponent;
import org.patryk3211.powergrid.circuits.components.properties.ComponentProperty;
import org.patryk3211.powergrid.circuits.components.properties.EnumProperty;
import org.patryk3211.powergrid.circuits.components.properties.IntProperty;
import org.patryk3211.powergrid.circuits.components.properties.Orientation;
import org.patryk3211.powergrid.circuits.schematic.ComponentFootprint;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;
import org.patryk3211.powergrid.circuits.thermal.ThermalBuilder;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.PNJunctionWire;
import org.patryk3211.powergrid.utility.Lang;

import com.power.morediodes.components.functions.LedPhysics;
import com.power.morediodes.components.functions.RepairSupport;

import java.util.List;

/**
 * 5x5 latching LED matrix, 13 pins. Orientable like every other component.
 *
 * Default orientation (5 x 5 footprint):
 *   top row    (y=0): cathodes C1..C5, left to right  = matrix columns, west to east
 *   middle row (y=2): VCC, CLEAR, GND on the three middle pins
 *   bottom row (y=4): anodes   A1..A5, left to right  = matrix rows, north to south
 * Node indices: 0..4 cathodes, 5..9 anodes, 10 VCC, 11 CLEAR, 12 GND.
 *
 * Behaviour: the diode at (row r, column c) sits between anode r and cathode c and is a normal
 * red-LED PN junction. While it conducts (anode high, cathode low) the cell is "powered". Each time
 * a cell BECOMES powered (rising edge), its held bit is XORed. Examples on a blank matrix: cathodes
 * 10000 + anodes 11011 give
 *   01111 / 01111 / 00000 / 01111 / 01111.
 * Because everything is decided from real current through the diodes, a loose pin or an open
 * switch never counts as a logic 0 - nothing flows, so nothing is powered.
 *
 * Supply: VCC-GND is a small resistive load. It needs the same voltage as the diodes (the
 * matrixDiodeActivationVoltage config, 10 % tolerance) for the matrix to work; if VCC or GND is lost the held data is cleared.
 * CLEAR is a pull-down input: while it is held high (CLEAR to GND voltage above
 * MIN_CLEAR_VOLTAGE) all held data is cleared and edges are ignored.
 *
 * Burnout: every diode has its own thermal unit - lights from 2 V (activation, config) and never burns
 * at or below the matrixDiodeVoltage config (5 V); more voltage across anode/cathode burns that one diode. The VCC-GND load has one more thermal unit (index 25);
 * too much voltage between VCC and GND burns the whole matrix (all 25 diodes).
 * Burnt diodes only exist in the BURNT bit mask; creative players repair by sneak + right-click.
 *
 * Held data (STATE) and burnt diodes (BURNT) are hidden synced properties, bit = row * 5 + column.
 */
public class LedMatrixComponent extends OrientableComponent
        implements IRenderedComponent, IComponentGoggleInformation, IInteractableComponent {

    /** Multiplier from the dye colour to the emissive LED colour. */
    private static final float LED_BRIGHTEN = 1.45f;

    public static final int SIZE = 5;
    public static final int DIODES = SIZE * SIZE;
    public static final int ALL = (1 << DIODES) - 1;

    public static final int PIN_CATHODE_0 = 0;
    public static final int PIN_ANODE_0 = 5;
    public static final int PIN_VCC = 10;
    public static final int PIN_CLEAR = 11;
    public static final int PIN_GND = 12;

    /** Thermal unit index of the VCC-GND load (after the 25 diode units). */
    private static final int SUPPLY_UNIT = DIODES;

    /** A cell counts as powered above this fraction of the rated diode current. */
    private static final double POWERED_FRACTION = 0.25D;

    private static final float SUPPLY_LOAD_OHMS = 1000f;
    private static final float CLEAR_LOAD_OHMS = 10000f;
    /** VCC-GND must reach this fraction of the diode activation voltage (the supply needs the same voltage as the diodes). */
    private static final double SUPPLY_NEEDED_FRACTION = 0.9D;
    private static final double MIN_CLEAR_VOLTAGE = 1.0D;

    /** Ticks after a (re)bake during which the held data is neither changed nor cleared (solver warm-up). */
    private static final int WARMUP_TICKS = 10;
    /** Consecutive ticks without a good supply before the held data is cleared. */
    private static final int SUPPLY_LOSS_TICKS = 2;

    /** LED colour of the whole matrix, picked in the board editor like the Power Grid light bulb. Default red. */
    public static final EnumProperty<DyeColor> COLOR = new EnumProperty<DyeColor>(MoreDiodes.MODID, "color",
            DyeColor.class, DyeColor.values(), DyeColor.RED);

    public static final IntProperty STATE = (IntProperty)
            new IntProperty(MoreDiodes.MODID, "matrix_state", 0, 0, ALL).hidden().unsafe();
    public static final IntProperty BURNT = (IntProperty)
            new IntProperty(MoreDiodes.MODID, "matrix_burnt", 0, 0, ALL).hidden().unsafe();

    public LedMatrixComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(LABEL);
        properties.add(COLOR);
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
        FloatingNode vcc = builder.terminalNode(PIN_VCC);
        FloatingNode clear = builder.terminalNode(PIN_CLEAR);
        FloatingNode gnd = builder.terminalNode(PIN_GND);

        // Diodes light from the activation voltage (rated current there) and burn above the max voltage.
        double activationVoltage = ConfigFile.MATRIX_DIODE_ACTIVATION_VOLTAGE.get();
        double maxVoltage = Math.max(ConfigFile.MATRIX_DIODE_VOLTAGE.get(), activationVoltage);
        double ratedCurrent = ConfigFile.MATRIX_DIODE_CURRENT_MA.get() / 1000.0D;
        double saturationCurrent = LedPhysics.saturationCurrent(activationVoltage, ratedCurrent);
        float burnPower = LedPhysics.burnPower(activationVoltage, ratedCurrent, maxVoltage);

        int burnt = placed.get(BURNT);
        PNJunctionWire[] leds = new PNJunctionWire[DIODES];
        for (int i = 0; i < DIODES; i++) {
            final int diode = i;
            // Always request a thermal unit, in order, so saved temperatures keep a stable index.
            var thermal = LedPhysics.burnAt(thermals.builder(), burnPower)
                    .withOverheatCallback(() -> burn(placed, 1 << diode));
            if ((burnt & (1 << i)) != 0)
                continue;
            int row = i / SIZE, col = i % SIZE;
            // PNJunctionWire(node1 = anode, node2 = cathode)
            var led = new PNJunctionWire(saturationCurrent, LedPhysics.SERIES_R, LedPhysics.TEMP_C,
                    LedPhysics.IDEALITY, LedPhysics.BREAKDOWN_V, LedPhysics.BREAKDOWN_IS, anodes[row], cathodes[col]);
            builder.add(led);
            leds[i] = led;
            thermal.addHeatSource(led);
        }

        // VCC-GND load: its current tells whether both supply pins are connected and powered.
        ElectricWire supply = builder.connect(SUPPLY_LOAD_OHMS, vcc, gnd);
        // The supply must be able to carry the diode activation voltage, so the burn limit is never below it.
        double maxSupply = Math.max(ConfigFile.MATRIX_MAX_SUPPLY_VOLTAGE.get(), activationVoltage);
        LedPhysics.burnAt(thermals.builder(), LedPhysics.resistorBurnPower(maxSupply, SUPPLY_LOAD_OHMS))
                .withOverheatCallback(() -> burn(placed, ALL))
                .addHeatSource(supply);
        LedPhysics.addSentinelUnit(thermals); // keep last: see LedPhysics.addSentinelUnit

        // CLEAR is a pull-down input: floating CLEAR draws no current and therefore is NOT high.
        ElectricWire clearWire = builder.connect(CLEAR_LOAD_OHMS, clear, gnd);

        // VCC-GND is "good" when it carries the diode activation voltage (10 % tolerance).
        double supplyCurrentNeeded = SUPPLY_NEEDED_FRACTION * activationVoltage / SUPPLY_LOAD_OHMS;
        placed.customData = new Runtime(leds, supply, clearWire, ratedCurrent, supplyCurrentNeeded);
    }

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        if (placed.isClient())
            return false;
        if (!(placed.customData instanceof Runtime rt))
            return true;

        int burnt = placed.get(BURNT);
        int powered = 0;
        for (int i = 0; i < DIODES; i++) {
            if ((burnt & (1 << i)) != 0 || rt.leds[i] == null)
                continue;
            if (rt.leds[i].current() >= POWERED_FRACTION * rt.ratedCurrent)
                powered |= 1 << i;
        }

        if (rt.age < WARMUP_TICKS) {
            rt.age++;
            rt.prevPowered = powered;
            return true;
        }

        boolean supplyGood = rt.supply.current() >= rt.supplyCurrentNeeded;
        boolean clearHigh = rt.clear.current() >= MIN_CLEAR_VOLTAGE / CLEAR_LOAD_OHMS;

        int state = placed.get(STATE);
        if (!supplyGood) {
            if (++rt.badTicks >= SUPPLY_LOSS_TICKS)
                state = 0;
        } else {
            rt.badTicks = 0;
            if (clearHigh)
                state = 0;
            else
                state ^= powered & ~rt.prevPowered; // XOR on the rising edge of every powered cell
        }
        rt.prevPowered = powered;

        state &= ~burnt;
        if (state != placed.get(STATE)) {
            placed.set(STATE, state);
            placed.notifyClients(STATE);
        }
        return true;
    }

    /** Overheat callback: PowerGrid has already removed the heat-source wires; only record it. */
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

    /** Rotating in the creative board editor rebuilds the board cold, so burnt state and data go too. */
    @Override
    public boolean rotate(@NotNull PlacedComponent placed, boolean counterClockwise) {
        boolean rotated = super.rotate(placed, counterClockwise);
        if (rotated) {
            placed.set(BURNT, 0);
            placed.set(STATE, 0);
        }
        return rotated;
    }

    /** Only a damaged matrix is interactable (creative repair); otherwise a speck at the footprint corner. */
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

        // Every burnt diode and the supply load start cool again; other parts keep their heat.
        RepairSupport.rebuild(be, placed, DIODES + 1,
                index -> index == SUPPLY_UNIT || (burnt & (1 << index)) != 0,
                () -> {
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
        if (state == 0)
            return;

        renderCells(ms, bufferSource, placed.get(ORIENTATION), state, placed.get(COLOR));
    }

    /** Draws the lit LED dots of a 5x5 matrix; bit = row * 5 + column. Shared with the simple matrix. */
    static void renderCells(PoseStack ms, MultiBufferSource bufferSource, Orientation orientation, int state,
                            DyeColor color) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());

        // Dye colour like the Power Grid bulb, brightened because the dots are drawn emissive
        // (RED comes out as a bright red, about 255/67/55).
        int dye = color.getTextureDiffuseColor();
        int r = Mth.clamp((int) (((dye >> 16) & 0xFF) * LED_BRIGHTEN), 0, 255);
        int g = Mth.clamp((int) (((dye >> 8) & 0xFF) * LED_BRIGHTEN), 0, 255);
        int b = Mth.clamp((int) ((dye & 0xFF) * LED_BRIGHTEN), 0, 255);

        // Same transform Power Grid applies to the baked model (the footprint is square).
        float size = SIZE / 16f;
        ms.pushPose();
        switch (orientation) {
            case DOWN -> ms.translate(size, 0, 0);
            case LEFT -> ms.translate(size, 0, size);
            case UP -> ms.translate(0, 0, size);
            default -> {
            }
        }
        ms.mulPose(new Quaternionf().rotationY(orientation.ordinal() * (float) Math.PI * 0.5f));
        Matrix4f pose = ms.last().pose();

        float y = 2.03f / 16f; // just above the 2 px high body
        for (int i = 0; i < DIODES; i++) {
            if ((state & (1 << i)) == 0)
                continue;
            int row = i / SIZE, col = i % SIZE;
            // The LED dot is the middle half of its 1 px cell, like the texture.
            float x0 = (col + 0.25f) / 16f, x1 = (col + 0.75f) / 16f;
            float z0 = (row + 0.25f) / 16f, z1 = (row + 0.75f) / 16f;
            quad(buffer, pose, x0, x1, y, z0, z1, r, g, b, 255);
            float h = 1 / 64f;
            quad(buffer, pose, x0 - h, x1 + h, y - 0.0005f, z0 - h, z1 + h, r * 3 / 4, g * 3 / 4, b * 3 / 4, 64);
        }
        ms.popPose();
    }

    private static void quad(VertexConsumer b, Matrix4f pose, float x0, float x1, float y, float z0, float z1,
                              int r, int g, int bl, int a) {
        b.addVertex(pose, x0, y, z0).setColor(r, g, bl, a);
        b.addVertex(pose, x0, y, z1).setColor(r, g, bl, a);
        b.addVertex(pose, x1, y, z1).setColor(r, g, bl, a);
        b.addVertex(pose, x1, y, z0).setColor(r, g, bl, a);

        b.addVertex(pose, x1, y, z0).setColor(r, g, bl, a);
        b.addVertex(pose, x1, y, z1).setColor(r, g, bl, a);
        b.addVertex(pose, x0, y, z1).setColor(r, g, bl, a);
        b.addVertex(pose, x0, y, z0).setColor(r, g, bl, a);
    }

    @Override
    public boolean addToGoggleTooltip(@NotNull PlacedComponent placed, @NotNull List<Component> tooltip,
                                       boolean isPlayerSneaking) {
        int damaged = Integer.bitCount(placed.get(BURNT));
        ComponentLabels.addToTooltip(placed, tooltip);
        ModLang.text("item.morediodes.led_matrix_advanced").forGoggles(tooltip);
        ModLang.text("tooltip.morediodes.damaged_diodes", damaged, DIODES)
                .style(damaged == 0 ? ChatFormatting.GREEN : ChatFormatting.RED).forGoggles(tooltip);
        if (damaged != 0)
            if (ModLang.localPlayerIsCreative())
                ModLang.text("tooltip.morediodes.repair").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip);
        return true;
    }

    /** Per-placement live wires (null diodes are burnt), stashed via {@link PlacedComponent#customData}. */
    private static final class Runtime {
        final PNJunctionWire[] leds;
        final ElectricWire supply;
        final ElectricWire clear;
        final double ratedCurrent;
        final double supplyCurrentNeeded;
        int age;
        int badTicks;
        int prevPowered;

        Runtime(PNJunctionWire[] leds, ElectricWire supply, ElectricWire clear, double ratedCurrent,
                double supplyCurrentNeeded) {
            this.leds = leds;
            this.supply = supply;
            this.clear = clear;
            this.ratedCurrent = ratedCurrent;
            this.supplyCurrentNeeded = supplyCurrentNeeded;
        }
    }
}

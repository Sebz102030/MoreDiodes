package com.power.morediodes.components;

import com.google.common.collect.ImmutableCollection;
import com.mojang.blaze3d.vertex.PoseStack;
import com.power.morediodes.MoreDiodes;
import com.simibubi.create.foundation.render.RenderTypes;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.circuitboard.ComponentCircuitBuilder;
import org.patryk3211.powergrid.circuits.components.IInteractableComponent;
import org.patryk3211.powergrid.circuits.components.IRenderedComponent;
import org.patryk3211.powergrid.circuits.components.OrientableComponent;
import org.patryk3211.powergrid.circuits.components.properties.ComponentProperty;
import org.patryk3211.powergrid.circuits.components.properties.IntProperty;
import org.patryk3211.powergrid.circuits.schematic.ComponentFootprint;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;
import org.patryk3211.powergrid.circuits.thermal.ThermalBuilder;
import org.patryk3211.powergrid.electricity.sim.special.PNJunctionWire;

public class ComponentDiodeWhite extends OrientableComponent implements IRenderedComponent {
    public static final int PIN_ANODE   = 0;
    public static final int PIN_KATHODE = 1;

    // ---- Electrical limits ----------------------------------------------------------------
    /** Maximum current (A). Heat from this current is exactly what the thermal unit can dissipate. */
    public static final float MAX_CURRENT = 0.2f;
    /** Current at which the junction drops exactly the enum voltage = full brightness (A). */
    public static final double RATED_CURRENT = 0.02;
    /** Thermal unit overheats (LED burns out) at this temperature (C). */
    public static final float OVERHEAT_TEMPERATURE = 150f;
    /** Voltage window (V) below the enum voltage over which the LED ramps from dark to full. */
    public static final float GLOW_WINDOW = 0.25f;

    private static final double SERIES_RESISTANCE = 0.5;
    private static final double IDEALITY = 2.0;
    private static final double REVERSE_BREAKDOWN = 5.0; // like a real LED
    private static final double TEMPERATURE_C = 22.0;

    // ---- Synced (hidden) brightness: server computes it from voltage, client only renders it --
    public static final IntProperty BRIGHTNESS =
            new IntProperty(MoreDiodes.MODID, "brightness", 0, 0, 100).hidden().unsafe().cast();

    public enum DiodeColour {
        // runVoltage = forward voltage: brightest here, burns out when pushed past MAX_CURRENT
        white(DyeColor.WHITE, 4f),
        blue(DyeColor.BLUE, 3.2f),
        yellow(DyeColor.YELLOW, 2.2f),
        red(DyeColor.RED, 2.1f),
        green(DyeColor.GREEN, 2.5f);

        final float RunVoltage;
        final DyeColor color;

        DiodeColour(DyeColor color, float runVoltage) {
            this.RunVoltage = runVoltage;
            this.color = color;
        }
    }

    /** Client render state. */
    private static class Brightness {
        float prev, current;

        float lerped(float partial) {
            return Mth.lerp(partial, prev, current);
        }
    }

    private final DiodeColour colour;

    public ComponentDiodeWhite(ComponentFootprint footprint) {
        this(footprint, DiodeColour.white);
    }

    public ComponentDiodeWhite(ComponentFootprint footprint, DiodeColour colour) {
        super(footprint);
        this.colour = colour;
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(current(MAX_CURRENT), BRIGHTNESS);
    }

    public VoxelShape getShape(@NotNull PlacedComponent placed) {
        return IInteractableComponent.extrudedFootprint(placed, 3 / 16f);
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, ThermalBuilder.@NotNull IEmitter thermals) {
        // Pick the saturation current so the junction drops exactly the enum voltage at RATED_CURRENT:
        //   Vf = n*Vt*ln(I/Is + 1) + I*Rs
        double vt = 1.380649e-23 * (TEMPERATURE_C + 273.15) / 1.602176634e-19;
        double nVt = IDEALITY * vt;
        double saturation = RATED_CURRENT / Math.expm1((colour.RunVoltage - RATED_CURRENT * SERIES_RESISTANCE) / nVt);

        // node1 = anode, node2 = cathode -> potentialDifference() > 0 when forward biased.
        var wire = new PNJunctionWire(saturation, SERIES_RESISTANCE, TEMPERATURE_C, IDEALITY,
                REVERSE_BREAKDOWN, 1e-6,
                builder.terminalNode(PIN_ANODE), builder.terminalNode(PIN_KATHODE));
        builder.add(wire);
        placed.add(wire);

        // Power dissipated at MAX_CURRENT: V(I) = n*Vt*ln(I/Is + 1) + I*Rs
        double vAtMax = nVt * Math.log(MAX_CURRENT / saturation + 1.0) + MAX_CURRENT * SERIES_RESISTANCE;
        float maxPower = (float) (vAtMax * MAX_CURRENT);

        // Burnout is purely thermal: at MAX_CURRENT the diode settles just below the overheat
        // temperature, anything more heats it past it and ThermalUnit removes the wire (burnt out).
        thermals.builder()
                .addHeatSource(wire)
                .setThermalMass(0.01f)
                .setMaxPower(maxPower, OVERHEAT_TEMPERATURE - 5f)
                .setOverheatTemperature(OVERHEAT_TEMPERATURE)
                .withTemperatureCallback(wire::setTemperatureCelsius);
    }

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        placed.onClientWorld(() -> world -> clientTick(placed));
        placed.onServerWorld(() -> world -> serverTick(placed));
        return true;
    }

    private void serverTick(PlacedComponent placed) {
        if (placed.wires.isEmpty())
            return;
        var wire = placed.wires.get(0);

        // Wire removed by the thermal unit = burnt out -> dark.
        double v = wire.getNetwork() == null ? 0.0 : wire.potentialDifference();
        if (wire.getNetwork() != null && !wire.isConverged())
            return;

        // Brightness from junction voltage: 0 at (Vf - window), 1 at Vf, stays 1 above it.
        double vf = colour.RunVoltage;
        double x = Mth.clamp((v - (vf - GLOW_WINDOW)) / GLOW_WINDOW, 0.0, 1.0);
        int percent = (int) Math.round(x * x * 100.0);
        if (percent != placed.get(BRIGHTNESS)) {
            placed.set(BRIGHTNESS, percent);
            placed.notifyClients(BRIGHTNESS);
        }
    }

    private void clientTick(PlacedComponent placed) {
        Brightness data;
        if (placed.customData instanceof Brightness b) {
            data = b;
        } else {
            data = new Brightness();
            placed.customData = data;
        }
        float target = placed.get(BRIGHTNESS) / 100f;
        data.prev = data.current;
        data.current += (target - data.current) * 0.5f;
        if (Math.abs(target - data.current) < 0.01f)
            data.current = target;
    }

    @Override
    public void render(CircuitBoardBlockEntity be, PlacedComponent placed, float partialTicks, PoseStack ms, MultiBufferSource bufferSource, int light, int overlay) {
        var glowModel = (this.colour == DiodeColour.white) ? MoreDiodes.GLOW : MoreDiodes.GLOW_DYED;

        var color = this.colour.color.getTextureDiffuseColor();
        var red   = (color >> 16) & 0xFF;
        var green = (color >> 8) & 0xFF;
        var blue  = color & 0xFF;

        int a = 0;
        if (placed.customData instanceof Brightness data) {
            a = (int) (data.lerped(partialTicks) * 255);
        }
        if (a == 0)
            return;

        var center = 1f / 16f;
        var orientation = placed.get(ORIENTATION);

        var buffer = CachedBuffers.partial(glowModel, be.getBlockState());
        buffer
                .disableDiffuse()
                .color(red, green, blue, a)
                .light(LightTexture.FULL_BRIGHT)
                .translate(center, center, center)
                .rotateYDegrees(orientation.ordinal() * 90)
                .translateBack(center, center, center)
                .renderInto(ms, bufferSource.getBuffer(RenderTypes.additive()));
    }
}
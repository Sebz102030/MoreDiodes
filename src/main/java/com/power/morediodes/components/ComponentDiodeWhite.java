package com.power.morediodes.components;

import com.power.morediodes.MoreDiodes;

import org.patryk3211.powergrid.circuits.components.OrientableComponent;
import org.patryk3211.powergrid.circuits.components.properties.IntProperty;
import org.patryk3211.powergrid.circuits.schematic.ComponentFootprint;
import org.patryk3211.powergrid.circuits.components.properties.ComponentProperty;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;
import org.patryk3211.powergrid.circuits.components.IInteractableComponent;
import org.patryk3211.powergrid.circuits.circuitboard.ComponentCircuitBuilder;
import org.patryk3211.powergrid.circuits.thermal.ThermalBuilder;
import org.patryk3211.powergrid.electricity.sim.special.PNJunctionWire;
import org.patryk3211.powergrid.collections.ModdedPartialModels;
import org.patryk3211.powergrid.circuits.components.IRenderedComponent;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.components.properties.CalculatedProperty;
import org.patryk3211.powergrid.circuits.components.properties.FloatProperty;
import org.patryk3211.powergrid.utility.Unit;
import org.patryk3211.powergrid.PowerGrid;

import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import net.minecraft.util.Mth;
import com.google.common.collect.ImmutableCollection;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.render.RenderTypes;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.DyeColor;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;


public class ComponentDiodeWhite extends OrientableComponent implements IRenderedComponent {
    public static final int PIN_ANODE   = 0;
    public static final int PIN_KATHODE = 1;

    public static final int DEFAULT_VOLTAGE    = 12;
    public static final int MIN_TARGET_VOLTAGE = 2;
    public static final int MAX_TARGET_VOLTAGE = 30;

    public enum DiodeColour {
        white(DyeColor.WHITE, 4f),
        blue(DyeColor.BLUE, 3.2f),
        yellow(DyeColor.YELLOW, 2.2f),
        red(DyeColor.RED, 2.1f),
        green(DyeColor.GREEN, 2.5f);

        final float RunVoltage;
        final DyeColor color;

        DiodeColour(DyeColor color, float runVoltage) {
            this.RunVoltage = runVoltage;
            this.color = color;}
    }

    private static class Brightness {
        float prev, current;
 
        float lerped(float partial) {
            return Mth.lerp(partial, prev, current);
        }
    }

    private final DiodeColour colour;


    public ComponentDiodeWhite(ComponentFootprint footprint){
        super(footprint);
        this.colour = DiodeColour.white;}

    public ComponentDiodeWhite(ComponentFootprint footprint, DiodeColour colour){
        super(footprint);
        this.colour = colour;
    }

    @Override 
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(current(0.2f));
    }

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        if(!placed.isClient())
            return false;
        renderDataTick(placed);
        return true;
    }

    public VoxelShape getShape(@NotNull PlacedComponent placed) {
        return IInteractableComponent.extrudedFootprint(placed, 3 / 16f);
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, ThermalBuilder.@NotNull IEmitter thermals) {
        var wire = new PNJunctionWire( 0f, 0.5f, 22f, 2f, 4f, 1e-6f,builder.terminalNode(0), builder.terminalNode(1));
        builder.add(wire);
        placed.add(wire);

        var data = new Brightness();
        placed.customData = data;
        thermals.builder()
                .addHeatSource(wire)
                .setThermalMass(0.001f)
                .setMaxPower(0.64f, 100f)
                .setOverheatTemperature(1850f)
                .withTemperatureCallback(T -> {
                    wire.setTemperatureCelsius(T);
                    var x = Mth.clamp((T - 25f) / (100f - 25f), 0, 1);
                    data.current = x * x;
                });
    }

    @Override
    public void render(CircuitBoardBlockEntity be, PlacedComponent placed, float partialTicks, PoseStack ms, MultiBufferSource bufferSource, int light, int overlay) {
        var glowModel = MoreDiodes.GLOW_DYED;
        if (this.colour == DiodeColour.white) {
            glowModel = MoreDiodes.GLOW;
        }

        var color = this.colour.color.getTextureDiffuseColor();
        var red   = (color >> 16) & 0xFF;
        var green = (color >> 8) & 0xFF;
        var blue  = color & 0xFF;

        int a = 0;
        if (placed.customData instanceof Brightness temps) {
            a = (int) (temps.lerped(partialTicks) * 255);
        }

        if (a == 0){
            return;}

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
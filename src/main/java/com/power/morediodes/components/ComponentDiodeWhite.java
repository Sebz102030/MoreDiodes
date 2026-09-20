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
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.ProvidedVoltageSourceCoupling;

import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import com.google.common.collect.ImmutableCollection;

public class ComponentDiodeWhite extends OrientableComponent /*implements IInteractableComponent, IRenderComponent*/ {
    public static final int PIN_ANODE   = 0;
    public static final int PIN_KATHODE = 1;

    public static final int DEFAULT_VOLTAGE    = 12;
    public static final int MIN_TARGET_VOLTAGE = 2;
    public static final int MAX_TARGET_VOLTAGE = 60;

    public static final IntProperty RUN_VOLTAGE =
        new IntProperty(MoreDiodes.MODID, "max_voltage",
            DEFAULT_VOLTAGE, MIN_TARGET_VOLTAGE, MAX_TARGET_VOLTAGE);

    private static final ComponentFootprint SMALL_FOOTPRINT = new ComponentFootprint.Builder(2, 1)
            .addPad(0, 0, PIN_ANODE).addPad(1, 0, PIN_KATHODE).withItem().withOutline().build();

    public ComponentDiodeWhite(ComponentFootprint footprint){
        super(footprint/* , SMALL_FOOTPRINT*/);
    }

    @Override 
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(RUN_VOLTAGE);
    }

    //@Override
    public VoxelShape getShape(@NotNull PlacedComponent placed) {
        return IInteractableComponent.extrudedFootprint(placed, 3 / 16f);
    }

    private static double computeSource(@NotNull PlacedComponent placed, FloatingNode anode, FloatingNode kathode) {
        double voltage = kathode.getVoltage()- anode.getVoltage();
        if (voltage < 0) return 0D;

        int LightAlpha = Math.round((float) (voltage * 255)/ placed.get(RUN_VOLTAGE));
        if (LightAlpha < 4) LightAlpha = 0;
        return LightAlpha ;
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder,
                      ThermalBuilder.@NotNull IEmitter thermals) {
        FloatingNode anode = builder.terminalNode(PIN_ANODE);
        FloatingNode kathode = builder.terminalNode(PIN_KATHODE);

        var source = new ProvidedVoltageSourceCoupling(anode, kathode, 1f);
        source.setVoltageProvider(() -> computeSource(placed, anode, kathode));
        builder.add(source);
        placed.add(source);
    }

    //public void render
}
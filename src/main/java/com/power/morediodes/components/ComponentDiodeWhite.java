package com.power.morediodes.components;

import com.power.morediodes.MoreDiodes;

class ComponentDiodeWhite extends OrientableComponent {
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
        super(footprint, SMALL_FOOTPRINT);
    }

    @Override 
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(RUN_VOLTAGE);
    }

    @Override
    public VoxelShape getShape(@NotNull PlacedComponent placed) {
        return IInteractableComponent.extrudedFootprint(placed, 3 / 16f);
    }
}
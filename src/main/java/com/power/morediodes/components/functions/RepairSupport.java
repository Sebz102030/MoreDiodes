package com.power.morediodes.components.functions;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;
import org.patryk3211.powergrid.circuits.thermal.ThermalUnit;

import java.util.function.IntPredicate;

/** Shared creative-mode repair helper of the LED-based components (7-segment displays and the LED matrix). */
public final class RepairSupport {

    /** Temperature a freshly repaired diode starts at. */
    static final float REPAIRED_TEMPERATURE = 20f;

    private RepairSupport() {
    }

    /**
     * Runs {@code mutate} (which should clear burnt bits) and rebuilds the board so the repaired
     * diodes rejoin the circuit. A rebuild resets every thermal unit on the board, which would
     * also "repair" other overheated parts, so the old temperatures are snapshotted first and read
     * back afterwards - except for the units {@code cool} selects, which start at a cool
     * temperature. Thermal units of this component are numbered in bake order, {@code unitCount} of them.
     */
    public static void rebuild(CircuitBoardBlockEntity be, PlacedComponent placed, int unitCount,
                        IntPredicate cool, Runnable mutate) {
        var level = be.getLevel();
        if (level == null || level.isClientSide)
            return;

        var baked = be.getBaked();
        CompoundTag snapshot = null;
        if (baked != null) {
            snapshot = new CompoundTag();
            baked.write(snapshot);
            CompoundTag thermal = snapshot.getCompound("Thermal");
            int index = 0;
            for (ThermalUnit unit : baked.thermalUnits) {
                if (!unit.getId().equals(placed.uuid))
                    continue;
                if (index < unitCount && cool.test(index))
                    thermal.putFloat(unit.getKey(), REPAIRED_TEMPERATURE);
                index++;
            }
        }

        mutate.run();
        be.setSchematic(be.getSchematic());
        if (snapshot != null)
            setAdditionalData(be, level.registryAccess(), snapshot);
        be.setChanged();
    }

    /**
     * Power Grid 0.6.2 has setAdditionalData(HolderLookup.Provider, CompoundTag); older builds had a
     * one-argument version. Calling through reflection works with whichever one is on the classpath
     * at runtime (a direct call crashed with NoSuchMethodError).
     */
    private static void setAdditionalData(CircuitBoardBlockEntity be, HolderLookup.Provider registries,
                                          CompoundTag snapshot) {
        try {
            be.getClass().getMethod("setAdditionalData", HolderLookup.Provider.class, CompoundTag.class)
                    .invoke(be, registries, snapshot);
            return;
        } catch (NoSuchMethodException ignored) {
            // fall through to the older signature
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        try {
            be.getClass().getMethod("setAdditionalData", CompoundTag.class).invoke(be, snapshot);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

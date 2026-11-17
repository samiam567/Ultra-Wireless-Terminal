package com.ultrawireless.terminal;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Everything needed to decide whether a linked terminal is "in range", mirroring Tom's
 * Storage's {@code StorageTerminalBlockEntity#canInteractWith}.
 *
 * <p>{@code beaconLvl} and {@code beaconLvlCrossDim} are Tom's Storage config values
 * ({@code -1} means "disabled").
 */
public record InRangeContext(
        ResourceKey<Level> playerDim,
        double px, double py, double pz,
        int range,                            // the held terminal's getRange()
        int beaconLvl,                        // -1 = beacon disabled
        int beaconLvlCrossDim,                // -1 = cross-dim beacon disabled
        boolean menuCheck) {

    /** Same-dimension, no beacon: the base reach used by Tom's Storage. */
    public static final int BASE_REACH = 6;
}

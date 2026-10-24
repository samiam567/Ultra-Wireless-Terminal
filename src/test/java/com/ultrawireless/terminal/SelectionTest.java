package com.ultrawireless.terminal;

import org.junit.jupiter.api.Test;

import com.tom.storagemod.components.WorldPos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SelectionTest {

    @Test
    void picksClosestInSameDimension() {
        WorldPos near = new WorldPos(Level.OVERWORLD, new BlockPos(10, 64, 10));
        WorldPos far = new WorldPos(Level.OVERWORLD, new BlockPos(100, 64, 100));
        WorldPos best = UltraWirelessTerminalItem.selectClosest(
                java.util.List.of(far, near), Level.OVERWORLD, 0, 64, 0);
        assertEquals(near, best);
    }

    @Test
    void prefersSameDimensionOverCrossDimension() {
        WorldPos sameDimFar = new WorldPos(Level.OVERWORLD, new BlockPos(100, 64, 100));
        WorldPos otherDimNear = new WorldPos(Level.NETHER, new BlockPos(1, 64, 1));
        WorldPos best = UltraWirelessTerminalItem.selectClosest(
                java.util.List.of(otherDimNear, sameDimFar), Level.OVERWORLD, 0, 64, 0);
        assertEquals(sameDimFar, best);
    }

    @Test
    void fallsBackToCrossDimensionWhenNoSameDimension() {
        WorldPos nether = new WorldPos(Level.NETHER, new BlockPos(5, 64, 5));
        WorldPos end = new WorldPos(Level.END, new BlockPos(1, 64, 1));
        WorldPos best = UltraWirelessTerminalItem.selectClosest(
                java.util.List.of(nether, end), Level.OVERWORLD, 0, 64, 0);
        // first available in list when all cross-dimension
        assertEquals(nether, best);
    }

    @Test
    void returnsNullForEmpty() {
        assertNull(UltraWirelessTerminalItem.selectClosest(java.util.List.of(), Level.OVERWORLD, 0, 64, 0));
    }
}

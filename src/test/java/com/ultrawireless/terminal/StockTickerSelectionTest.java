package com.ultrawireless.terminal;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tom.storagemod.components.WorldPos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StockTickerSelectionTest {

    @Test
    void selectsClosestStockTickerInSameDimension() {
        WorldPos near = new WorldPos(Level.OVERWORLD, new BlockPos(3, 64, 4));
        WorldPos far = new WorldPos(Level.OVERWORLD, new BlockPos(200, 64, 200));
        WorldPos best = UltraWirelessTerminalItem.selectClosest(List.of(far, near), Level.OVERWORLD, 0, 64, 0);
        assertEquals(near, best);
    }

    @Test
    void ignoresEmptyStockTickerList() {
        assertNull(UltraWirelessTerminalItem.selectClosest(List.of(), Level.OVERWORLD, 0, 64, 0));
    }

    @Test
    void prefersSameDimensionStockTickerOverOtherDimensions() {
        WorldPos endNear = new WorldPos(Level.END, new BlockPos(1, 64, 1));
        WorldPos overworldFar = new WorldPos(Level.OVERWORLD, new BlockPos(500, 64, 500));
        WorldPos best = UltraWirelessTerminalItem.selectClosest(List.of(endNear, overworldFar), Level.OVERWORLD, 0, 64, 0);
        assertEquals(overworldFar, best);
    }

    @Test
    void picksSingleTickerRegardlessOfOrder() {
        WorldPos only = new WorldPos(Level.OVERWORLD, new BlockPos(10, 64, 10));
        WorldPos first = UltraWirelessTerminalItem.selectClosest(List.of(only), Level.OVERWORLD, 0, 64, 0);
        WorldPos second = UltraWirelessTerminalItem.selectClosest(List.of(only), Level.OVERWORLD, 100, 64, 100);
        assertEquals(only, first);
        assertTrue(second.pos().equals(new BlockPos(10, 64, 10)));
    }
}

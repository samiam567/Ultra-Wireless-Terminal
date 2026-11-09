package com.ultrawireless.terminal;

import net.minecraft.world.level.block.Block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit tests for {@link UltraWirelessTerminalItem#isStockTicker(Block)}.
 *
 * Only the null-rejection path is safely unit-testable here: a real Create
 * stock-ticker block cannot be constructed in this harness (the Create mod and
 * the block registry are unavailable to the test classpath).
 */
public class IsStockTickerTest {

    @Test
    void returnsFalseForNullBlock() {
        // A null block must be rejected gracefully (false) rather than throwing
        // a NullPointerException from block.builtInRegistryHolder().
        assertFalse(UltraWirelessTerminalItem.isStockTicker((Block) null));
    }
}

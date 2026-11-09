package com.ultrawireless.terminal;

import com.tom.storagemod.components.WorldPos;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link UltraWirelessTerminalItem#selectClosest(List, ResourceKey, double, double, double)}.
 *
 * The existing {@link SelectionTest} covers the happy path. This class locks in the
 * robustness / edge-case contract of the selector:
 *
 * <ul>
 *   <li>null-tolerant iteration (a {@code null} in the candidate list must not
 *       {@code NullPointerException} and must not affect the result);</li>
 *   <li>deterministic first-wins tie-breaking for equal distances; </li>
 *   <li>tolerance for a {@code null} player dimension; and</li>
 *   <li>the rule that same-dimension proximity always beats cross-dimension, and that
 *       cross-dimension proximity is deliberately ignored.</li>
 * </ul>
 */
public class SelectionEdgeCasesTest {

    @Test
    void ignoresNullCandidates() {
        WorldPos close = new WorldPos(Level.OVERWORLD, new BlockPos(3, 64, 3));
        WorldPos far = new WorldPos(Level.OVERWORLD, new BlockPos(50, 64, 50));

        // Nulls mixed into the list must not throw and must not affect the result.
        WorldPos best = UltraWirelessTerminalItem.selectClosest(
                Arrays.asList(null, close, null, far, null),
                Level.OVERWORLD, 0, 64, 0);

        assertEquals(close, best);
    }

    @Test
    void returnsNullWhenEveryCandidateIsNull() {
        assertNull(UltraWirelessTerminalItem.selectClosest(
                Arrays.asList(null, null, null), Level.OVERWORLD, 0, 64, 0));
    }

    @Test
    void returnsNullWhenCandidatesAreEmpty() {
        assertNull(UltraWirelessTerminalItem.selectClosest(
                List.of(), Level.OVERWORLD, 0, 64, 0));
    }

    @Test
    void deterministicFirstOnEqualDistanceSameDimension() {
        // Both candidates share the player's dimension and have an identical
        // centre-based squared distance, so the earlier list element wins
        // (the selector uses a strict "<" comparison).
        WorldPos first = new WorldPos(Level.OVERWORLD, new BlockPos(2, 64, 0));
        WorldPos second = new WorldPos(Level.OVERWORLD, new BlockPos(0, 64, 2));
        // first:  2.5^2 + 0.5^2 + 0.5^2 = 6.75 ; second: 0.5^2 + 0.5^2 + 2.5^2 = 6.75

        WorldPos best = UltraWirelessTerminalItem.selectClosest(
                List.of(first, second), Level.OVERWORLD, 0, 64, 0);

        assertEquals(first, best);
    }

    @Test
    void nullPlayerDimensionDoesNotThrow() {
        // A null player dimension must be treated as "no same-dimension match"
        // rather than throwing a NullPointerException.
        WorldPos nether = new WorldPos(Level.NETHER, new BlockPos(5, 64, 5));

        WorldPos best = UltraWirelessTerminalItem.selectClosest(
                List.of(nether), null, 0, 64, 0);

        assertNotNull(best);
        assertEquals(nether, best);
    }

    @Test
    void sameDimensionAlwaysBeatsCrossDimension() {
        // A very far same-dimension candidate must beat a near cross-dimension
        // candidate, because cross-dimension proximity is deliberately ignored.
        WorldPos sameDimVeryFar = new WorldPos(Level.OVERWORLD, new BlockPos(5000, 64, 5000));
        WorldPos crossDimNear = new WorldPos(Level.NETHER, new BlockPos(1, 64, 1));

        WorldPos best = UltraWirelessTerminalItem.selectClosest(
                List.of(crossDimNear, sameDimVeryFar), Level.OVERWORLD, 0, 64, 0);

        assertEquals(sameDimVeryFar, best);
    }

    @Test
    void whenAllCrossDimensionFirstInListWinsRegardlessOfDistance() {
        // Documented behaviour: when no candidate shares the player's dimension,
        // raw proximity is never compared (every cross-dim candidate gets MAX_VALUE),
        // so the FIRST candidate in the list wins — even if another is geometrically
        // closer in raw coordinates.
        WorldPos endFar = new WorldPos(Level.END, new BlockPos(100, 64, 100));
        WorldPos netherNear = new WorldPos(Level.NETHER, new BlockPos(1, 64, 1));

        WorldPos best = UltraWirelessTerminalItem.selectClosest(
                List.of(endFar, netherNear), Level.OVERWORLD, 0, 64, 0);

        // endFar (100,64,100) is the FIRST element; netherNear (1,64,1) is geometrically
        // nearer to (0,64,0) but must NOT win because cross-dim distance is ignored.
        assertEquals(endFar, best);
    }
}

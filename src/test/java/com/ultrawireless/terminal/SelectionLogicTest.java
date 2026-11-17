package com.ultrawireless.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.tom.storagemod.components.WorldPos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for the v2 selection rules in {@link Selection}: keep-active, auto-select
 * nearest on open, and switch-only-while-in-range. These mirror the dependency-free
 * self-check in {@code tools/UWT2_Selection.java}.
 */
public class SelectionLogicTest {

    private static final String OVERWORLD = "minecraft:overworld";

    private static WorldPos pos(int x, int y, int z) {
        return new WorldPos(Level.OVERWORLD, new BlockPos(x, y, z));
    }

    private static WorldPos nether(int x, int y, int z) {
        return new WorldPos(Level.NETHER, new BlockPos(x, y, z));
    }

    // ================= resolveOnOpen =================

    @Test
    void keepsActiveTerminalWhenStillInRange() {
        WorldPos active = pos(10, 64, 10);
        WorldPos other = pos(100, 64, 100);
        WorldPos result = Selection.resolveOnOpen(List.of(active, other), active,
                wp -> true, OVERWORLD, 0, 64, 0);
        assertEquals(active, result);
    }

    @Test
    void autoSelectsRemainingTerminalWhenActiveOutOfRange() {
        WorldPos active = pos(10, 64, 10);
        WorldPos other = pos(100, 64, 100);
        Predicate<WorldPos> inRange = wp -> wp.equals(other);
        WorldPos result = Selection.resolveOnOpen(List.of(active, other), active,
                inRange, OVERWORLD, 0, 64, 0);
        assertEquals(other, result);
    }

    @Test
    void autoSelectsNearestWhenNoActiveSet() {
        WorldPos near = pos(10, 64, 10);
        WorldPos far = pos(100, 64, 100);
        WorldPos result = Selection.resolveOnOpen(List.of(far, near), null,
                wp -> true, OVERWORLD, 0, 64, 0);
        assertEquals(near, result);
    }

    @Test
    void autoSelectsNearestWhenActiveUnlinked() {
        WorldPos stale = pos(999, 64, 999); // no longer bound
        WorldPos bound = pos(10, 64, 10);
        WorldPos result = Selection.resolveOnOpen(List.of(bound), stale,
                wp -> true, OVERWORLD, 0, 64, 0);
        assertEquals(bound, result);
    }

    @Test
    void returnsNullWhenNothingInRange() {
        WorldPos result = Selection.resolveOnOpen(List.of(pos(10, 64, 10)), pos(10, 64, 10),
                wp -> false, OVERWORLD, 0, 64, 0);
        assertNull(result);
    }

    @Test
    void returnsNullForEmptyOrNullBoundList() {
        assertNull(Selection.resolveOnOpen(List.of(), null, wp -> true, OVERWORLD, 0, 0, 0));
        assertNull(Selection.resolveOnOpen(null, null, wp -> true, OVERWORLD, 0, 0, 0));
    }

    @Test
    void sameDimensionCandidateBeatsCrossDimension() {
        WorldPos netherNear = nether(1, 64, 1);
        WorldPos overworldFar = pos(100, 64, 100);
        WorldPos result = Selection.resolveOnOpen(List.of(netherNear, overworldFar), null,
                wp -> true, OVERWORLD, 0, 64, 0);
        assertEquals(overworldFar, result);
    }

    // ================= requestSwitch =================

    @Test
    void switchAllowedToBoundInRangeTerminal() {
        WorldPos a = pos(10, 64, 10);
        WorldPos b = pos(50, 64, 50);
        WorldPos result = Selection.requestSwitch(List.of(a, b), b, wp -> true);
        assertEquals(b, result);
    }

    @Test
    void switchRefusedWhenTargetOutOfRange() {
        WorldPos a = pos(10, 64, 10);
        WorldPos b = pos(50, 64, 50);
        assertNull(Selection.requestSwitch(List.of(a, b), b, wp -> wp.equals(a)));
    }

    @Test
    void switchRefusedWhenTargetNotBound() {
        WorldPos a = pos(10, 64, 10);
        WorldPos stranger = pos(50, 64, 50);
        assertNull(Selection.requestSwitch(List.of(a), stranger, wp -> true));
    }

    @Test
    void switchRefusedForNullArguments() {
        assertNull(Selection.requestSwitch(null, pos(1, 1, 1), wp -> true));
        assertNull(Selection.requestSwitch(List.of(pos(1, 1, 1)), null, wp -> true));
    }

    // ================= selectClosest contract =================

    @Test
    void selectClosestTolerantsNullsAndFirstWinsTies() {
        WorldPos first = pos(10, 64, 10);
        WorldPos tied = pos(10, 64, 10);
        List<WorldPos> candidates = new ArrayList<>();
        candidates.add(null);
        candidates.add(first);
        candidates.add(tied);
        assertEquals(first, Selection.selectClosest(candidates, OVERWORLD, 0, 64, 0));
    }

    @Test
    void selectClosestWithNullPlayerDimPicksFirst() {
        WorldPos a = pos(10, 64, 10);
        WorldPos b = pos(5, 64, 5);
        assertEquals(a, Selection.selectClosest(List.of(a, b), null, 0, 64, 0));
    }

    @Test
    void distanceUsesBlockCenter() {
        WorldPos p = pos(0, 64, 0);
        double d = Selection.distanceSqr(p, OVERWORLD, 0.5, 64.5, 0.5);
        assertEquals(0.0, d, 1e-9);
        assertEquals(Double.MAX_VALUE, Selection.distanceSqr(nether(0, 64, 0), OVERWORLD, 0, 0, 0), 0.0);
    }
}

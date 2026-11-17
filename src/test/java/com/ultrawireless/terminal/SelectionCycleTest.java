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
 * Tests for {@link Selection#cycle} - the fallback keybind rule: walk in-range bound
 * terminals in list order, wrapping around.
 */
public class SelectionCycleTest {

    private static WorldPos pos(int x) {
        return new WorldPos(Level.OVERWORLD, new BlockPos(x, 64, 0));
    }

    /** All in range. */
    private static final Predicate<WorldPos> ALL = wp -> true;

    /** Nothing at x = 99 is reachable. */
    private static final Predicate<WorldPos> NOT_99 = wp -> wp.pos().getX() != 99;

    @Test
    void forwardFromActive() {
        List<WorldPos> bound = List.of(pos(1), pos(2), pos(3));
        assertEquals(pos(2), Selection.cycle(bound, pos(1), 1, ALL));
        assertEquals(pos(3), Selection.cycle(bound, pos(2), 1, ALL));
        assertEquals(pos(1), Selection.cycle(bound, pos(3), 1, ALL), "wraps forward");
    }

    @Test
    void backwardFromActive() {
        List<WorldPos> bound = List.of(pos(1), pos(2), pos(3));
        assertEquals(pos(3), Selection.cycle(bound, pos(1), -1, ALL));
        assertEquals(pos(2), Selection.cycle(bound, pos(3), -1, ALL));
        assertEquals(pos(1), Selection.cycle(bound, pos(2), -1, ALL));
    }

    @Test
    void nullActiveStartsAtFirstOrLast() {
        List<WorldPos> bound = List.of(pos(1), pos(2), pos(3));
        assertEquals(pos(1), Selection.cycle(bound, null, 1, ALL));
        assertEquals(pos(3), Selection.cycle(bound, null, -1, ALL));
    }

    @Test
    void staleActiveSkipped() {
        List<WorldPos> bound = List.of(pos(1), pos(2), pos(3));
        WorldPos stale = pos(99); // not in the bound list
        assertEquals(pos(1), Selection.cycle(bound, stale, 1, ALL));
        assertEquals(pos(3), Selection.cycle(bound, stale, -1, ALL));
    }

    @Test
    void unreachableTerminalsExcluded() {
        WorldPos dead = pos(99);
        List<WorldPos> bound = List.of(pos(1), dead, pos(3));
        // from pos(1) the next reachable is pos(3), skipping the out-of-range one
        assertEquals(pos(3), Selection.cycle(bound, pos(1), 1, NOT_99));
        assertEquals(pos(1), Selection.cycle(bound, pos(3), 1, NOT_99), "wraps past dead entry");
        assertEquals(pos(3), Selection.cycle(bound, pos(1), -1, NOT_99));
    }

    @Test
    void twoEntriesToggleBothDirections() {
        List<WorldPos> bound = List.of(pos(1), pos(2));
        assertEquals(pos(2), Selection.cycle(bound, pos(1), 1, ALL));
        assertEquals(pos(1), Selection.cycle(bound, pos(2), 1, ALL));
        assertEquals(pos(2), Selection.cycle(bound, pos(1), -1, ALL));
    }

    @Test
    void singleEntryStays() {
        List<WorldPos> bound = List.of(pos(1));
        assertEquals(pos(1), Selection.cycle(bound, pos(1), 1, ALL));
        assertEquals(pos(1), Selection.cycle(bound, pos(1), -1, ALL));
    }

    @Test
    void nothingReachableReturnsNull() {
        assertNull(Selection.cycle(List.of(pos(99)), pos(99), 1, NOT_99));
    }

    @Test
    void emptyAndNullTolerated() {
        assertNull(Selection.cycle(List.of(), null, 1, ALL));
        assertNull(Selection.cycle(null, null, 1, ALL));
        assertNull(Selection.cycle(List.of(pos(1)), null, 1, null));
    }

    @Test
    void nullEntriesInListSkipped() {
        List<WorldPos> bound = new ArrayList<>();
        bound.add(pos(1));
        bound.add(null);
        bound.add(pos(2));
        assertEquals(pos(2), Selection.cycle(bound, pos(1), 1, ALL));
        assertEquals(pos(1), Selection.cycle(bound, pos(2), 1, ALL));
    }
}

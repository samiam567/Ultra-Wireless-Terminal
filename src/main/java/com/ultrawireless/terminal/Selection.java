package com.ultrawireless.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.tom.storagemod.components.WorldPos;

/**
 * Pure selection rules for the single-active-terminal design. No Minecraft world access,
 * so every rule is unit-testable ({@code SelectionLogicTest}) and mirrored dependency-free
 * in {@code tools/UWT2_Selection.java}.
 *
 * <ul>
 *   <li>{@link #resolveOnOpen}: keep the active terminal if still bound &amp; reachable,
 *       otherwise fall back to the nearest in-range terminal (auto-select).</li>
 *   <li>{@link #requestSwitch}: only allow switching to a bound, currently-in-range
 *       terminal (the selector GUI exposes exactly those as clickable buttons).</li>
 *   <li>{@link #selectClosest}: same contract as the v1 selector — same-dimension
 *       candidates always beat cross-dimension ones; equal distances keep the first
 *       candidate seen; {@code null}s and null player dimension are tolerated.</li>
 * </ul>
 */
public final class Selection {

    private Selection() {}

    /** Squared distance to the block center; {@link Double#MAX_VALUE} for other dimensions. */
    public static double distanceSqr(WorldPos wp, String playerDim, double px, double py, double pz) {
        if (wp == null) {
            return Double.MAX_VALUE;
        }
        boolean sameDim = playerDim != null && wp.dim().location().toString().equals(playerDim);
        if (!sameDim) {
            return Double.MAX_VALUE;
        }
        double dx = wp.pos().getX() + 0.5D - px;
        double dy = wp.pos().getY() + 0.5D - py;
        double dz = wp.pos().getZ() + 0.5D - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Nearest candidate preferring the player dimension; null-safe, first-wins on ties. */
    public static WorldPos selectClosest(List<WorldPos> candidates, String playerDim,
            double px, double py, double pz) {
        WorldPos best = null;
        double bestDist = Double.MAX_VALUE;
        if (candidates == null) {
            return null;
        }
        for (WorldPos wp : candidates) {
            if (wp == null) {
                continue;
            }
            double dist = distanceSqr(wp, playerDim, px, py, pz);
            if (best == null || dist < bestDist) {
                best = wp;
                bestDist = dist;
            }
        }
        return best;
    }

    /**
     * Which terminal to open (and keep/set active) when the player opens the item.
     *
     * @param bound     all bound terminals
     * @param active    stored active terminal (may be null / stale / out of range)
     * @param inRange   server-side reachability test (chunk loaded, block intact, in range)
     * @param playerDim player dimension id string
     * @return the terminal to open now, or {@code null} when none is reachable
     */
    public static WorldPos resolveOnOpen(List<WorldPos> bound, WorldPos active,
            Predicate<WorldPos> inRange, String playerDim, double px, double py, double pz) {
        if (bound == null || bound.isEmpty()) {
            return null;
        }
        if (active != null && bound.contains(active) && inRange.test(active)) {
            return active;
        }
        List<WorldPos> reachable = new ArrayList<>();
        for (WorldPos wp : bound) {
            if (wp != null && inRange.test(wp)) {
                reachable.add(wp);
            }
        }
        return selectClosest(reachable, playerDim, px, py, pz);
    }

    /**
     * Selector-button click: the clicked terminal becomes active only while bound and
     * in range; otherwise {@code null} (the current selection is left untouched).
     */
    public static WorldPos requestSwitch(List<WorldPos> bound, WorldPos clicked,
            Predicate<WorldPos> inRange) {
        if (bound == null || clicked == null || inRange == null) {
            return null;
        }
        if (!bound.contains(clicked) || !inRange.test(clicked)) {
            return null;
        }
        return clicked;
    }

    /**
     * Fallback cycle keybind: pick the next (dir &gt; 0) or previous (dir &lt; 0)
     * in-range terminal in {@code bound} order, wrapping around. When the current active
     * terminal is null / unlinked / out of range, forward starts at the first in-range
     * entry and backward at the last. Returns {@code null} when none are in range.
     *
     * <p>Order is the (stable) bound list, not distance — cycling must be predictable,
     * not jump around as the player moves. Tolerates nulls in the list.
     */
    public static WorldPos cycle(List<WorldPos> bound, WorldPos active, int dir,
            Predicate<WorldPos> inRange) {
        if (bound == null || bound.isEmpty() || inRange == null) {
            return null;
        }
        List<WorldPos> reachable = new ArrayList<>();
        for (WorldPos wp : bound) {
            if (wp != null && inRange.test(wp)) {
                reachable.add(wp);
            }
        }
        if (reachable.isEmpty()) {
            return null;
        }
        int n = reachable.size();
        int step = dir >= 0 ? 1 : -1;
        int idx = reachable.indexOf(active); // -1 when active null / not reachable
        int next = idx < 0
                ? (step > 0 ? 0 : n - 1)
                : Math.floorMod(idx + step, n);
        return reachable.get(next);
    }
}

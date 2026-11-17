// Ultra Wireless Terminal 2 - pure selection logic.
//
// This file has ZERO external dependencies (java.lang / java.util only) so it can be
// compiled and unit-tested with a plain `javac` on any machine. The production mod
// packages this same code as `src/main/java/com/ultrawireless/terminal/Selection.java`
// (identical bodies; there the position type is the stub record
// `com.tom.storagemod.components.WorldPos` and the dimension is a string compared with
// `equals`). Keep the two copies in sync when changing rules, and mirror every change in
// the JUnit tests under `src/test/java` too.
//
// Behavior implemented here (the whole point of v2):
//   * One bound terminal is "active" at a time.
//   * The player can switch the active terminal to any OTHER bound terminal that is
//     currently in range (selector GUI buttons).
//   * If the active terminal is out of range (or gone) when the terminal is next opened,
//     the NEAREST in-range terminal is selected instead and becomes the new active one.
//   * "Nearest" prefers the player's own dimension; a same-dimension candidate always
//     beats a cross-dimension one, and cross-dimension distance is ignored entirely.

import java.util.ArrayList;
import java.util.List;

public final class UWT2_Selection {

    private UWT2_Selection() {}

    /** Minimal stand-in for {@code WorldPos}: dimension string + block coords. */
    public static final class Pos {
        public final String dim;
        public final int x;
        public final int y;
        public final int z;

        public Pos(String dim, int x, int y, int z) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Pos p)) return false;
            return x == p.x && y == p.y && z == p.z && dim.equals(p.dim);
        }

        @Override
        public int hashCode() {
            return dim.hashCode() * 31 * 31 + x * 31 * 31 + y * 31 + z;
        }

        @Override
        public String toString() {
            return dim + "@" + x + "," + y + "," + z;
        }
    }

    /**
     * Squared distance from the player's position to the center of this block.
     * Returns {@link Double#MAX_VALUE} for a different dimension.
     */
    public static double distSqrTo(Pos p, String playerDim, double px, double py, double pz) {
        boolean sameDim = playerDim != null && p.dim.equals(playerDim);
        if (!sameDim) {
            return Double.MAX_VALUE;
        }
        double dx = p.x + 0.5D - px;
        double dy = p.y + 0.5D - py;
        double dz = p.z + 0.5D - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Picks the closest candidate, preferring the player's dimension (same rule as v1's
     * {@code selectClosest}): nulls skipped; a same-dimension candidate always beats a
     * cross-dimension one; equal distances keep the FIRST candidate seen; a null player
     * dimension makes every candidate "cross-dimension", so the first non-null wins.
     * Returns null for a null/empty list.
     */
    public static Pos selectClosest(List<Pos> candidates, String playerDim,
            double px, double py, double pz) {
        Pos best = null;
        double bestDist = Double.MAX_VALUE;
        if (candidates == null) {
            return null;
        }
        for (Pos p : candidates) {
            if (p == null) {
                continue;
            }
            double dist = distSqrTo(p, playerDim, px, py, pz);
            if (best == null || dist < bestDist) {
                best = p;
                bestDist = dist;
            }
        }
        return best;
    }

    /**
     * Resolve which terminal should be active when the player opens the item.
     *
     * @param bound      all bound terminals (in link order)
     * @param active     the stored active terminal (may be null / stale / unbound)
     * @param inRange    predicate: terminal currently usable (loaded + block intact + range)
     * @param playerDim  player's dimension id string (may be null)
     * @param px,py,pz   player position
     * @return the terminal to open now (also the new "active" terminal), or null when
     *         nothing is reachable
     */
    public static Pos resolveOnOpen(List<Pos> bound, Pos active,
            java.util.function.Predicate<Pos> inRange,
            String playerDim, double px, double py, double pz) {
        if (bound == null || bound.isEmpty()) {
            return null;
        }
        // keep the current selection when it is still bound and reachable
        if (active != null && bound.contains(active) && inRange.test(active)) {
            return active;
        }
        // otherwise fall back to the nearest reachable terminal
        List<Pos> reachable = new ArrayList<>();
        for (Pos p : bound) {
            if (p != null && inRange.test(p)) {
                reachable.add(p);
            }
        }
        return selectClosest(reachable, playerDim, px, py, pz);
    }

    /**
     * Switch the active terminal. Only allowed when the clicked terminal is bound AND
     * currently in range; otherwise the current selection is left untouched.
     *
     * @return the new active terminal, or null if the switch was refused
     */
    public static Pos requestSwitch(List<Pos> bound, Pos clicked,
            java.util.function.Predicate<Pos> inRange) {
        if (bound == null || clicked == null) {
            return null;
        }
        if (!bound.contains(clicked) || !inRange.test(clicked)) {
            return null;
        }
        return clicked;
    }

    /**
     * Fallback cycle keybind: next/previous in-range terminal in bound order, wrapping.
     * Mirrors Selection.cycle exactly.
     */
    public static Pos cycle(List<Pos> bound, Pos active, int dir,
            java.util.function.Predicate<Pos> inRange) {
        if (bound == null || bound.isEmpty() || inRange == null) {
            return null;
        }
        List<Pos> reachable = new ArrayList<>();
        for (Pos p : bound) {
            if (p != null && inRange.test(p)) {
                reachable.add(p);
            }
        }
        if (reachable.isEmpty()) {
            return null;
        }
        int n = reachable.size();
        int step = dir >= 0 ? 1 : -1;
        int idx = reachable.indexOf(active);
        int next = idx < 0
                ? (step > 0 ? 0 : n - 1)
                : Math.floorMod(idx + step, n);
        return reachable.get(next);
    }

    // ---------------------- self test: javac + java, no JUnit needed ----------------------

    public static void main(String[] args) {
        int failures = 0;
        Pos near = new Pos("minecraft:overworld", 10, 64, 10);
        Pos far = new Pos("minecraft:overworld", 100, 64, 100);
        Pos netherNear = new Pos("minecraft:the_nether", 1, 64, 1);

        // keep active when still in range
        Pos r = resolveOnOpen(List.of(near, far), near, p -> true, "minecraft:overworld", 0, 64, 0);
        failures += check(near, r, "keep active in range");

        // active out of range -> nearest in-range takes over
        r = resolveOnOpen(List.of(far, near), near, p -> p.equals(far), "minecraft:overworld", 0, 64, 0);
        failures += check(far, r, "active out of range -> remaining active");

        // active out of range, several in range -> nearest wins
        r = resolveOnOpen(List.of(far, near), null, p -> true, "minecraft:overworld", 0, 64, 0);
        failures += check(near, r, "no active -> nearest");

        // same-dim beats cross-dim even when much farther
        r = resolveOnOpen(List.of(netherNear, far), null, p -> true, "minecraft:overworld", 0, 64, 0);
        failures += check(far, r, "same dim beats cross dim");

        // nothing in range -> null
        r = resolveOnOpen(List.of(near, far), near, p -> false, "minecraft:overworld", 0, 64, 0);
        failures += check(null, r, "none in range -> null");

        // switch refused when target not in range
        r = requestSwitch(List.of(near, far), far, p -> p.equals(near));
        failures += check(null, r, "switch refused out of range");

        // switch accepted when target bound + in range
        r = requestSwitch(List.of(near, far), far, p -> true);
        failures += check(far, r, "switch accepted");

        // switch refused when not bound
        r = requestSwitch(List.of(near), far, p -> true);
        failures += check(null, r, "switch refused unbound");

        // cycle: next/previous in-range terminal in bound order, wrapping
        Pos t1 = new Pos("minecraft:overworld", 1, 64, 0);
        Pos t2 = new Pos("minecraft:overworld", 2, 64, 0);
        Pos t3 = new Pos("minecraft:overworld", 3, 64, 0);
        failures += check(t2, cycle(List.of(t1, t2, t3), t1, 1, p -> true), "cycle forward");
        failures += check(t1, cycle(List.of(t1, t2, t3), t3, 1, p -> true), "cycle forward wraps");
        failures += check(t3, cycle(List.of(t1, t2, t3), t1, -1, p -> true), "cycle backward wraps");
        failures += check(t1, cycle(List.of(t1, t2, t3), null, 1, p -> true), "cycle null active forward");
        failures += check(t3, cycle(List.of(t1, t2, t3), null, -1, p -> true), "cycle null active backward");
        Pos dead = new Pos("minecraft:overworld", 99, 64, 0);
        failures += check(t3, cycle(List.of(t1, dead, t3), t1, 1, p -> p.x != 99), "cycle skips unreachable");
        failures += check(null, cycle(List.of(dead), null, 1, p -> p.x != 99), "cycle none reachable -> null");

        System.out.println(failures == 0 ? "SELFTEST PASS" : "SELFTEST FAIL (" + failures + ")");
        if (failures != 0) {
            System.exit(1);
        }
    }

    private static int check(Object expected, Object actual, String name) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        System.out.println((ok ? "  ok  " : " FAIL ") + name
                + (ok ? "" : " (expected " + expected + ", got " + actual + ")"));
        return ok ? 0 : 1;
    }
}

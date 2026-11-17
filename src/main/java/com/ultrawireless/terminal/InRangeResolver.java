package com.ultrawireless.terminal;

import com.tom.storagemod.components.WorldPos;

/**
 * Pure replica of Tom's Storage's per-terminal range decision
 * ({@code StorageTerminalBlockEntity#canInteractWith}) for a single linked
 * terminal. The beacon config values are supplied via {@link InRangeContext}
 * (read server-side from Tom's Storage's config via {@link BeaconConfig}).
 *
 * <p>{@code menuCheck} is {@code false} when used for opening a single terminal
 * (a plain menu), matching {@code useWithoutItem}
 * ({@code canInteractWith(player, false)}).
 */
public final class InRangeResolver {

    private InRangeResolver() {
    }

    /**
     * Is the given terminal in range for the player described by {@code ctx}, given the
     * terminal's own beacon level ({@code -1} = none)?
     */
    public static boolean isInRange(WorldPos wp, InRangeContext ctx, int beaconLevel) {
        if (wp == null || ctx == null) {
            return false;
        }
        boolean sameDim = ctx.playerDim() != null && wp.dim().equals(ctx.playerDim());
        int termReach = ctx.range();
        if (ctx.beaconLvl() != -1 && beaconLevel >= ctx.beaconLvl() && termReach > 0) {
            if (ctx.beaconLvlCrossDim() != -1 && beaconLevel >= ctx.beaconLvlCrossDim()) {
                return true;
            }
            return sameDim;
        }
        int d = Math.max(InRangeContext.BASE_REACH, termReach);
        if (ctx.menuCheck()) {
            d += d / 4;
        }
        if (!sameDim) {
            return false;
        }
        double dx = wp.pos().getX() + 0.5D - ctx.px();
        double dy = wp.pos().getY() + 0.5D - ctx.py();
        double dz = wp.pos().getZ() + 0.5D - ctx.pz();
        double distSqr = dx * dx + dy * dy + dz * dz;
        double d2 = (double) d * d;
        return distSqr <= d2;
    }

    /**
     * Filters a candidate list to those in range, reading each terminal's beacon level
     * via the supplied {@code beaconLevelFor} function (null or returning null = no beacon).
     */
    public static java.util.List<WorldPos> resolve(java.util.List<WorldPos> candidates, InRangeContext ctx,
            java.util.function.Function<WorldPos, Integer> beaconLevelFor) {
        java.util.List<WorldPos> out = new java.util.ArrayList<>();
        if (candidates == null || ctx == null) {
            return out;
        }
        for (WorldPos wp : candidates) {
            if (wp == null) {
                continue;
            }
            int beaconLevel;
            if (beaconLevelFor == null) {
                beaconLevel = -1;
            } else {
                Integer b = beaconLevelFor.apply(wp);
                beaconLevel = b == null ? -1 : b;
            }
            if (isInRange(wp, ctx, beaconLevel)) {
                out.add(wp);
            }
        }
        return out;
    }
}

package com.ultrawireless.terminal;

import com.tom.storagemod.components.WorldPos;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-side "is this linked terminal usable right now" check.
 *
 * <p>A terminal is usable when:
 * <ul>
 *   <li>its own level is loaded at its position (which may be a different dimension
 *       than the player's);</li>
 *   <li>the block there is still a Tom's Storage remote-activate terminal;</li>
 *   <li>it passes Tom's Storage's beacon-aware range rule (see
 *       {@link InRangeResolver} / {@link InRangeContext}).</li>
 * </ul>
 *
 * <p>Beacon levels are read from the terminal's block entity by reflection
 * (Tom's Storage's {@code StorageTerminalBlockEntity#getBeaconLevel()}); a
 * {@code -1} (none/unknown) falls back to plain-distance range.
 */
public final class InRangeChecker {

    private InRangeChecker() {}

    /** The range context for a player holding a terminal with the given reach. */
    public static InRangeContext contextFor(ServerPlayer player, int termReach) {
        return new InRangeContext(
                player.level().dimension(),
                player.getX(), player.getY(), player.getZ(),
                termReach,
                BeaconConfig.beaconLevel(),
                BeaconConfig.beaconLevelCrossDim(),
                false); // plain menu open, matching useWithoutItem -> canInteractWith(player, false)
    }

    /** True when the terminal is loaded, still a terminal block, and within range. */
    public static boolean isAvailableAndInRange(ServerPlayer player, WorldPos terminal,
            InRangeContext ctx) {
        if (player == null || terminal == null) {
            return false;
        }
        Level termWorld = player.server.getLevel(terminal.dim());
        if (termWorld == null || !termWorld.isLoaded(terminal.pos())) {
            return false;
        }
        BlockState state = termWorld.getBlockState(terminal.pos());
        if (!state.is(UltraWirelessTerminalItem.REMOTE_ACTIVATE)) {
            return false;
        }
        BlockEntity be = termWorld.getBlockEntity(terminal.pos());
        int beaconLevel = beaconLevelOf(be);
        return InRangeResolver.isInRange(terminal, ctx, beaconLevel);
    }

    /** Beacon level of a block entity via reflection ({@code -1} = none/unknown). */
    public static int beaconLevelOf(BlockEntity be) {
        if (be == null) {
            return -1;
        }
        try {
            java.lang.reflect.Method m = be.getClass().getMethod("getBeaconLevel");
            Object v = m.invoke(be);
            if (v instanceof Number n) {
                return n.intValue();
            }
            return -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }
}

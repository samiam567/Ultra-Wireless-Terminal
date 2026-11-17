package com.ultrawireless.terminal;

import com.tom.storagemod.components.WorldPos;

/**
 * Stable string key for a terminal's player-given name, used as the map key inside the
 * {@code TERMINAL_NAMES} data component. {@code WorldPos.CODEC} encodes to a JSON object,
 * but {@code Codec.unboundedMap}'s keys must be JSON <em>strings</em>, so names cannot be
 * keyed by {@link WorldPos} directly — this compact {@code dim|x|y|z} form is.
 *
 * <p>Format is deliberately independent of mojmap / obfuscation: the namespace + path of
 * the dimension resource key, plus the block's three ints.
 */
public final class TerminalNameKey {

    private TerminalNameKey() {}

    public static String of(WorldPos wp) {
        if (wp == null) {
            return "";
        }
        return of(wp.dim().location().toString(), wp.pos().getX(), wp.pos().getY(), wp.pos().getZ());
    }

    public static String of(String dimName, int x, int y, int z) {
        return dimName + "|" + x + "|" + y + "|" + z;
    }
}

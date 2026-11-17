package com.ultrawireless.terminal;

/**
 * Server→client description of one linked terminal for the selector GUI.
 *
 * <p>{@code x}/{@code y}/{@code z} are the terminal's block coordinates.
 * {@code dimName} is the dimension's resource key string
 * ({@code minecraft:overworld}, {@code minecraft:nether}, ...).
 *
 * <p>{@code inRange} / {@code active} are computed server-side. The terminal's
 * {@code index} in the in-range list is implicit (its list position); the
 * selector sends it back in {@link ActivatePacket} to select it.
 *
 * <p>{@code name} is the player-given display name (from {@link RenamePacket} / the
 * {@code TERMINAL_NAMES} data component) or null when the player has not renamed it; the
 * selector then shows the coordinate label.
 */
public record TerminalInfo(String dimName, int x, int y, int z,
        boolean inRange, boolean active, String name) {

    /** Back-compat constructor: no custom name. */
    public TerminalInfo(String dimName, int x, int y, int z, boolean inRange, boolean active) {
        this(dimName, x, y, z, inRange, active, null);
    }

    /** Human-friendly label used by the server for chat messages. */
    public String label() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return "[" + dimName + "] " + x + ", " + y + ", " + z;
    }

    /** Key into the {@code TERMINAL_NAMES} map for this row. */
    public String nameKey() {
        return TerminalNameKey.of(dimName, x, y, z);
    }
}

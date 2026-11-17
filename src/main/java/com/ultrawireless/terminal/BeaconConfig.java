package com.ultrawireless.terminal;

/**
 * Reads Tom's Storage beacon range config fields
 * ({@code wirelessTermBeaconLvl}, {@code wirelessTermBeaconLvlCrossDim}) by
 * reflection. Tom's Storage's {@code Config} class is <em>not</em> available to
 * this mod at compile time (we only link against a stubs jar exposing
 * {@code WirelessTerminal} + {@code WorldPos}), so we reach into it at runtime.
 *
 * <p>{@code -1} means "disabled" — matching Tom's Storage's config defaults and
 * the meaning used by {@code StorageTerminalBlockEntity#canInteractWith}.
 */
public final class BeaconConfig {

    private BeaconConfig() {
    }

    /** Tom's Storage beacon-level threshold ({@code -1} = disabled). */
    public static int beaconLevel() {
        return readField("wirelessTermBeaconLvl");
    }

    /** Tom's Storage cross-dimension beacon-level threshold ({@code -1} = disabled). */
    public static int beaconLevelCrossDim() {
        return readField("wirelessTermBeaconLvlCrossDim");
    }

    /** Best-effort read of an {@code int} field off Tom's Storage's singleton config. */
    private static int readField(String name) {
        try {
            Class<?> configClass = Class.forName("com.tom.storagemod.Config");
            java.lang.reflect.Method getMethod = configClass.getMethod("get");
            Object config = getMethod.invoke(null);
            if (config == null) {
                return -1;
            }
            java.lang.reflect.Field field = configClass.getField(name);
            int value = field.getInt(config);
            return value;
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }
}

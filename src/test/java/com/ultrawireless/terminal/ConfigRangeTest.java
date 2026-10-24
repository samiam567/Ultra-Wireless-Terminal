package com.ultrawireless.terminal;

import org.junit.jupiter.api.Test;

import com.tom.storagemod.Config;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConfigRangeTest {
    @Test
    void rangeReadsFromConfig() {
        int original = Config.get().advWirelessRange;
        try {
            Config.get().advWirelessRange = 128;
            assertEquals(128, UltraWirelessTerminalItem.getConfiguredRange());
        } finally {
            Config.get().advWirelessRange = original;
        }
    }
}

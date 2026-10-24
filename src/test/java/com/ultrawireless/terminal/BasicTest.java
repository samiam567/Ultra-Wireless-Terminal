package com.ultrawireless.terminal;

import org.junit.jupiter.api.Test;

import com.tom.storagemod.components.WorldPos;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BasicTest {
    @Test
    void worldPosEquality() {
        WorldPos a = new WorldPos(Level.OVERWORLD, new BlockPos(1,2,3));
        WorldPos b = new WorldPos(Level.OVERWORLD, new BlockPos(1,2,3));
        assertEquals(a, b);
    }
}

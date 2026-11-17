package com.ultrawireless.terminal;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: cycle the active terminal to the next (or previous) in-range bound
 * terminal, following the bound list order. Sent by the fallback cycle keybinds, which
 * ship unbound by default (for setups where opening the selector GUI is awkward).
 */
public record CyclePacket(int direction) implements CustomPacketPayload {

    public static final Type<CyclePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UltraWirelessTerminalMod.MODID, "cycle_terminal"));

    public static final StreamCodec<ByteBuf, CyclePacket> CODEC =
            ByteBufCodecs.VAR_INT.map(CyclePacket::new, CyclePacket::direction);

    @Override
    public Type<CyclePacket> type() {
        return TYPE;
    }
}

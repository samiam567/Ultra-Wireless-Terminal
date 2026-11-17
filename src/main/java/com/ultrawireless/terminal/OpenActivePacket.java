package com.ultrawireless.terminal;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Request from the client to open the terminal fast path (right-click in air). The
 * server opens the currently active terminal if it is still in range; otherwise it
 * automatically selects and opens the nearest in-range terminal (setting it as active).
 * Shows "no terminals in range" if none.
 */
public record OpenActivePacket() implements CustomPacketPayload {

    public static final Type<OpenActivePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UltraWirelessTerminalMod.MODID, "open_active"));

    public static final StreamCodec<FriendlyByteBuf, OpenActivePacket> CODEC =
            EmptyPayload.codecOf(OpenActivePacket::new);

    @Override
    public Type<OpenActivePacket> type() {
        return TYPE;
    }
}

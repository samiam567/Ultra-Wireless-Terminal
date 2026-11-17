package com.ultrawireless.terminal;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Request from the client to re-send the {@link TerminalListPacket}. Sent when Tom's
 * Storage's terminal GUI opens so the overlay's buttons reflect the current in-range /
 * active state without reopening anything.
 */
public record RefreshListPacket() implements CustomPacketPayload {

    public static final Type<RefreshListPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UltraWirelessTerminalMod.MODID, "refresh_list"));

    public static final StreamCodec<FriendlyByteBuf, RefreshListPacket> CODEC =
            EmptyPayload.codecOf(RefreshListPacket::new);

    @Override
    public Type<RefreshListPacket> type() {
        return TYPE;
    }
}

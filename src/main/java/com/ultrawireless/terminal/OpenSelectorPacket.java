package com.ultrawireless.terminal;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Request from the client to open the terminal selector overlay. Sent when the player
 * presses the selector keybind. The server computes the in-range terminals + the active
 * terminal and replies with {@link TerminalListPacket}.
 */
public record OpenSelectorPacket() implements CustomPacketPayload {

    public static final Type<OpenSelectorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UltraWirelessTerminalMod.MODID, "open_selector"));

    public static final StreamCodec<FriendlyByteBuf, OpenSelectorPacket> CODEC =
            EmptyPayload.codecOf(OpenSelectorPacket::new);

    @Override
    public Type<OpenSelectorPacket> type() {
        return TYPE;
    }
}

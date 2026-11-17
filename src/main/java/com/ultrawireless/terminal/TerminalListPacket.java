package com.ultrawireless.terminal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client response to {@link OpenSelectorPacket} and to any activation/refresh
 * that changes the selection. Carries every bound terminal with its in-range / active
 * flags so the client can render one button per bound terminal (clickable only while
 * in range; the active one is highlighted).
 */
public record TerminalListPacket(List<TerminalInfo> terminals) implements CustomPacketPayload {

    public static final Type<TerminalListPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UltraWirelessTerminalMod.MODID, "terminal_list"));

    public static final StreamCodec<FriendlyByteBuf, TerminalListPacket> CODEC =
            StreamCodec.of(TerminalListPacket::encode, TerminalListPacket::decode);

    private static void encode(FriendlyByteBuf buf, TerminalListPacket packet) {
        buf.writeVarInt(packet.terminals().size());
        for (TerminalInfo info : packet.terminals()) {
            buf.writeUtf(info.dimName(), 256);
            buf.writeInt(info.x());
            buf.writeInt(info.y());
            buf.writeInt(info.z());
            buf.writeBoolean(info.inRange());
            buf.writeBoolean(info.active());
            // name is optional: write a presence flag so null and "" round-trip distinctly
            String name = info.name();
            buf.writeBoolean(name != null);
            if (name != null) {
                buf.writeUtf(name, 128);
            }
        }
    }

    private static TerminalListPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<TerminalInfo> terminals = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String dimName = buf.readUtf(256);
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            boolean inRange = buf.readBoolean();
            boolean active = buf.readBoolean();
            String name = buf.readBoolean() ? buf.readUtf(128) : null;
            terminals.add(new TerminalInfo(dimName, x, y, z, inRange, active, name));
        }
        return new TerminalListPacket(List.copyOf(terminals));
    }

    @Override
    public Type<TerminalListPacket> type() {
        return TYPE;
    }

    /** The active terminal, or empty when none is set. */
    public java.util.Optional<TerminalInfo> activeTerminal() {
        for (TerminalInfo t : terminals) {
            if (t.active()) {
                return java.util.Optional.of(t);
            }
        }
        return java.util.Optional.empty();
    }
}

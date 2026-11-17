package com.ultrawireless.terminal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: the stock network summary of the nearest linked Create stock ticker,
 * shown when the ticker is out of Create's own reach and its GUI would auto-close after
 * one tick ({@code MenuBase.stillValid}). The client renders it in
 * {@link StockSummaryScreen}.
 */
public record StockSummaryPacket(List<String> lines) implements CustomPacketPayload {

    public static final Type<StockSummaryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UltraWirelessTerminalMod.MODID, "stock_summary"));

    public static final StreamCodec<FriendlyByteBuf, StockSummaryPacket> CODEC =
            StreamCodec.of(StockSummaryPacket::encode, StockSummaryPacket::decode);

    private static void encode(FriendlyByteBuf buf, StockSummaryPacket packet) {
        buf.writeVarInt(packet.lines().size());
        for (String line : packet.lines()) {
            buf.writeUtf(line, 512);
        }
    }

    private static StockSummaryPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            lines.add(buf.readUtf(512));
        }
        return new StockSummaryPacket(List.copyOf(lines));
    }

    @Override
    public Type<StockSummaryPacket> type() {
        return TYPE;
    }
}

package com.ultrawireless.terminal;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class UltraWirelessTerminalPacket implements CustomPacketPayload {

	public static final Type<UltraWirelessTerminalPacket> OPEN_STOCK_TICKER_ID =
			new Type<>(ResourceLocation.fromNamespaceAndPath(UltraWirelessTerminalMod.MODID, "open_stock_ticker"));

	public static final StreamCodec<FriendlyByteBuf, UltraWirelessTerminalPacket> OPEN_STOCK_TICKER_CODEC =
			CustomPacketPayload.codec((a, b) -> {}, UltraWirelessTerminalPacket::new);

	public UltraWirelessTerminalPacket() {}

	private UltraWirelessTerminalPacket(FriendlyByteBuf buffer) {}

	@Override
	public Type<UltraWirelessTerminalPacket> type() {
		return OPEN_STOCK_TICKER_ID;
	}
}

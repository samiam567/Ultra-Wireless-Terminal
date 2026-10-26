package com.ultrawireless.terminal;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

public final class UltraWirelessTerminalClient {

	public static KeyMapping openStockTickerKey;

	private UltraWirelessTerminalClient() {}

	public static void register(IEventBus modEventBus) {
		modEventBus.addListener(UltraWirelessTerminalClient::registerKeyMappings);
		modEventBus.addListener(UltraWirelessTerminalClient::onClientTick);
	}

	private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
		openStockTickerKey = new KeyMapping(
				"key.ultra_wireless_terminal.open_stock_ticker",
				KeyConflictContext.IN_GAME,
				InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_N),
				KeyMapping.CATEGORY_GAMEPLAY);
		event.register(openStockTickerKey);
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		if (Minecraft.getInstance().player == null)
			return;
		if (openStockTickerKey.consumeClick())
			UltraWirelessTerminalNetworkHandler.sendOpenStockTicker();
	}
}

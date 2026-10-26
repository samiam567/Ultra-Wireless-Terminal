package com.ultrawireless.terminal;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class UltraWirelessTerminalNetworkHandler {

	private UltraWirelessTerminalNetworkHandler() {}

	public static void onPayloadRegister(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");
		registrar.playToServer(UltraWirelessTerminalPacket.OPEN_STOCK_TICKER_ID,
				UltraWirelessTerminalPacket.OPEN_STOCK_TICKER_CODEC,
				UltraWirelessTerminalNetworkHandler::handleOpenStockTickerServer);
	}

	private static void handleOpenStockTickerServer(UltraWirelessTerminalPacket packet, IPayloadContext context) {
		context.enqueueWork(() -> {
			ServerPlayer sender = (ServerPlayer) context.player();
			ItemStack stack = findTerminal(sender);
			if (stack.isEmpty())
				return;
			UltraWirelessTerminalItem.activateStockTicker(sender, stack);
		});
	}

	public static void sendOpenStockTicker() {
		PacketDistributor.sendToServer(new UltraWirelessTerminalPacket());
	}

	private static ItemStack findTerminal(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().items.size(); i++) {
			ItemStack item = player.getInventory().getItem(i);
			if (!item.isEmpty() && item.getItem() instanceof UltraWirelessTerminalItem)
				return item;
		}
		return findCuriosTerminal(player);
	}

	private static ItemStack findCuriosTerminal(ServerPlayer player) {
		try {
			Class<?> curiosCap = Class.forName("top.theillusivec4.curios.api.CuriosCapability");
			Object inventoryKey = curiosCap.getField("INVENTORY").get(null);
			Method getCapability = player.getClass().getMethod("getCapability", inventoryKey.getClass());
			Object handler = getCapability.invoke(player, inventoryKey);
			if (handler == null)
				return null;
			Method findCurios = handler.getClass().getMethod("findCurios", Predicate.class);
			for (Object curio : (List<?>) findCurios.invoke(handler,
					(Predicate<ItemStack>) stack -> stack.getItem() instanceof UltraWirelessTerminalItem)) {
				ItemStack item = (ItemStack) curio.getClass().getMethod("stack").invoke(curio);
				if (item.getItem() instanceof UltraWirelessTerminalItem)
					return item;
			}
		} catch (ReflectiveOperationException e) {
			return null;
		}
		return null;
	}
}

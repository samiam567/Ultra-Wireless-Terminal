package com.ultrawireless.terminal;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.tom.storagemod.components.WorldPos;

@net.neoforged.fml.common.Mod(UltraWirelessTerminalMod.MODID)
public class UltraWirelessTerminalMod {

	public static final String MODID = "ultra_wireless_terminal";

	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
	public static final DeferredRegister<net.minecraft.core.component.DataComponentType<?>> COMPONENTS =
			DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);

	public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<List<WorldPos>>> BOUND_TERMINALS =
			COMPONENTS.register("bound_terminals", () ->
					net.minecraft.core.component.DataComponentType.<List<WorldPos>>builder()
							.persistent(WorldPos.CODEC.listOf())
							.build());

	public static final DeferredHolder<Item, Item> ULTRA_WIRELESS_TERMINAL =
			ITEMS.register("ultra_wireless_terminal", () -> new UltraWirelessTerminalItem(new Item.Properties().stacksTo(1)));

	public UltraWirelessTerminalMod(IEventBus modEventBus) {
		ITEMS.register(modEventBus);
		COMPONENTS.register(modEventBus);
		modEventBus.addListener(this::addCreative);
	}

	private void addCreative(BuildCreativeModeTabContentsEvent event) {
		ResourceKey<CreativeModeTab> tomsTab = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
				ResourceLocation.fromNamespaceAndPath("toms_storage", "tab"));
		if (event.getTabKey().equals(tomsTab)) {
			event.accept(ULTRA_WIRELESS_TERMINAL.get());
		}
	}
}

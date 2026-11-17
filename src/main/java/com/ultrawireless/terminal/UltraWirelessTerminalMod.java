package com.ultrawireless.terminal;

import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;

import com.tom.storagemod.components.WorldPos;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@net.neoforged.fml.common.Mod(UltraWirelessTerminalMod.MODID)
public class UltraWirelessTerminalMod {

    public static final String MODID = "ultra_wireless_terminal_2";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    public static final DeferredRegister<net.minecraft.core.component.DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);

    /**
     * All the terminals linked to this item (a {@code List<WorldPos>}), stored as a
     * data component. Linked via sneak + right-click on a Tom's Storage remote-activate
     * terminal block.
     */
    public static final DeferredHolder<
            net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<List<WorldPos>>> BOUND_TERMINALS =
            COMPONENTS.register("bound_terminals", () ->
                    net.minecraft.core.component.DataComponentType.<List<WorldPos>>builder()
                            .persistent(WorldPos.CODEC.listOf())
                            .build());

    /**
     * The Create stock ticker this terminal is linked to (its {@code WorldPos}). Used to
     * open Create's <em>real</em> stock-ticker GUI (the one where you order items) while the
     * ticker is within reach (see {@link StockTickerProxy#openRemote} and the temporary
     * interaction-range buff that keeps it open).
     */
    public static final DeferredHolder<
            net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<WorldPos>> LINKED_STOCK_TICKER =
            COMPONENTS.register("linked_stock_ticker", () ->
                    net.minecraft.core.component.DataComponentType.<WorldPos>builder()
                            .persistent(WorldPos.CODEC)
                            .build());

    /**
     * The Create logistics network id (frequency) captured from the linked ticker. Lets the
     * terminal still list the network's contents from <em>anywhere</em> when the ticker is
     * too far to open its GUI, or has been broken (see {@link StockTickerProxy#networkSummary}).
     */
    public static final DeferredHolder<
            net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<java.util.UUID>> LINKED_STOCK_NETWORK =
            COMPONENTS.register("linked_stock_network", () ->
                    net.minecraft.core.component.DataComponentType.<java.util.UUID>builder()
                            .persistent(net.minecraft.core.UUIDUtil.CODEC)
                            .build());

    /**
     * Player-given display names for linked terminals, keyed by a stable serialized position
     * string ({@link TerminalNameKey#of}) rather than {@link WorldPos}: a data component
     * {@code Map}'s keys must encode to JSON <em>strings</em>, and {@code WorldPos.CODEC}
     * encodes to an object, so it can't be a map key directly. Absent key (or blank) means
     * the terminal falls back to its {@code dim x/y/z} coordinate label.
     */
    public static final DeferredHolder<
            net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<java.util.Map<String, String>>> TERMINAL_NAMES =
            COMPONENTS.register("terminal_names", () ->
                    net.minecraft.core.component.DataComponentType.<java.util.Map<String, String>>builder()
                            .persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING))
                            .build());

    /**
     * The currently active (selected) terminal. A single {@code WorldPos} that the player
     * has chosen to use via the overlay GUI buttons. When this terminal goes out of range,
     * the next open automatically selects the nearest in-range terminal (see
     * {@link Selection#resolveOnOpen}).
     */
    public static final DeferredHolder<
            net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<WorldPos>> ACTIVE_TERMINAL =
            COMPONENTS.register("active_terminal", () ->
                    net.minecraft.core.component.DataComponentType.<WorldPos>builder()
                            .persistent(WorldPos.CODEC)
                            .build());

    public static final DeferredHolder<Item, Item> ULTRA_WIRELESS_TERMINAL =
            ITEMS.register("ultra_wireless_terminal",
                    () -> new UltraWirelessTerminalItem(new Item.Properties().stacksTo(1)));

    public UltraWirelessTerminalMod(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(UltraWirelessTerminalNetworkHandler::onPayloadRegister);
        // grants/revokes the stock-ticker reach buff server-side when Create's stock menu
        // opens/closes; harmless no-op on the client (event never fires there)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(StockTickerBuffListener.class);
        if (FMLEnvironment.dist == Dist.CLIENT)
            UltraWirelessTerminalClient.register(modEventBus);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        ResourceKey<CreativeModeTab> tomsTab = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                ResourceLocation.fromNamespaceAndPath("toms_storage", "tab"));
        if (event.getTabKey().equals(tomsTab)) {
            event.accept(ULTRA_WIRELESS_TERMINAL.get());
        }
    }
}

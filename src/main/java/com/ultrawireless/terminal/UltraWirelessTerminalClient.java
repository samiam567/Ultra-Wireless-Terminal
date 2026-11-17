package com.ultrawireless.terminal;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

public final class UltraWirelessTerminalClient {

    public static KeyMapping openStockTickerKey;
    public static KeyMapping openSelectorKey;
    /** Fallback cycling keybinds, intentionally unbound by default. */
    public static KeyMapping cycleTerminalNextKey;
    public static KeyMapping cycleTerminalPreviousKey;

    private UltraWirelessTerminalClient() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(UltraWirelessTerminalClient::registerKeyMappings);
        NeoForge.EVENT_BUS.register(UltraWirelessTerminalClient.class);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        openStockTickerKey = new KeyMapping(
                "key.ultra_wireless_terminal_2.open_stock_ticker",
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_N),
                KeyMapping.CATEGORY_GAMEPLAY);
        event.register(openStockTickerKey);

        // UNIVERSAL (not IN_GAME) so these also match while a container screen is open:
        // IN_GAME.isActive() is literally "screen == null", which would make the
        // ScreenEvent.KeyPressed.Pre handler below dead. Vanilla still only registers
        // clicks with no screen, so pollKeybinds and the Pre handler can't double-fire.
        openSelectorKey = new KeyMapping(
                "key.ultra_wireless_terminal_2.open_selector",
                KeyConflictContext.UNIVERSAL,
                InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_M),
                KeyMapping.CATEGORY_GAMEPLAY);
        event.register(openSelectorKey);

        // Fallback cycling: for players who can't use the selector GUI. Unbound by
        // default (UNKNOWN key), so they only activate once assigned in Controls.
        cycleTerminalNextKey = new KeyMapping(
                "key.ultra_wireless_terminal_2.cycle_terminal_next",
                KeyConflictContext.UNIVERSAL,
                InputConstants.UNKNOWN,
                KeyMapping.CATEGORY_GAMEPLAY);
        event.register(cycleTerminalNextKey);

        cycleTerminalPreviousKey = new KeyMapping(
                "key.ultra_wireless_terminal_2.cycle_terminal_previous",
                KeyConflictContext.UNIVERSAL,
                InputConstants.UNKNOWN,
                KeyMapping.CATEGORY_GAMEPLAY);
        event.register(cycleTerminalPreviousKey);
    }

    @SubscribeEvent
    private static void onClientTick(ClientTickEvent.Post event) {
        pollKeybinds(Minecraft.getInstance());
    }

    /** Keybind polling, once per client tick (game keybinds only fire with no screen). */
    static void pollKeybinds(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        // game keybinds only fire with no screen open (Minecraft#handleKeybinds); the
        // in-screen case is handled by onScreenKeyPressed below
        if (openStockTickerKey.consumeClick()) {
            UltraWirelessTerminalNetworkHandler.sendOpenStockTicker();
        }
        if (openSelectorKey.consumeClick()) {
            // sets a flag; the server's TerminalListPacket reply opens the GUI layer
            TerminalOverlay.requestSelector();
        }
        if (cycleTerminalNextKey.consumeClick()) {
            UltraWirelessTerminalNetworkHandler.sendCycle(1);
        }
        if (cycleTerminalPreviousKey.consumeClick()) {
            UltraWirelessTerminalNetworkHandler.sendCycle(-1);
        }
        TerminalOverlay.tickRetry();
    }

    /**
     * Vanilla only dispatches keybind clicks while no screen is open, so the cycle
     * keybinds (and the selector) are checked here too — while a container GUI is up —
     * and cancel the key so it can't type into Tom's search field.
     */
    @SubscribeEvent
    private static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        // only while looking at a container (Tom's terminal, chests, ...): our own
        // selector / summary screens keep their normal key handling
        if (mc.player == null
                || !(event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
            return;
        }
        // isActiveAndMatches rejects UNKNOWN, so unbound-by-default keys never fire here
        InputConstants.Key pressed = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
        if (cycleTerminalNextKey.isActiveAndMatches(pressed)) {
            event.setCanceled(true);
            UltraWirelessTerminalNetworkHandler.sendCycle(1);
        } else if (cycleTerminalPreviousKey.isActiveAndMatches(pressed)) {
            event.setCanceled(true);
            UltraWirelessTerminalNetworkHandler.sendCycle(-1);
        } else if (openSelectorKey.isActiveAndMatches(pressed)) {
            event.setCanceled(true);
            TerminalOverlay.requestSelector();
        }
    }
}

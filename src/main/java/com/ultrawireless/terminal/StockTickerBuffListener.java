package com.ultrawireless.terminal;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

/**
 * Server-side glue for {@link StockTickerReach}: grants the reach buff the moment Create's
 * stock-ticker menu opens (for any reason — our keybind, sitting on a seat, another mod)
 * and revokes it the moment that menu closes. Everything else — chests, other Create
 * machines, vanilla — is untouched, so no buff ever leaks into unrelated interactions.
 *
 * <p>Identity is by class <em>name</em> because Create is an optional dependency: mod
 * class names are never obfuscated, so {@code equals} on the fully-qualified name is valid
 * in dev and production alike. Only {@code net.minecraft} names are.
 *
 * <p>Ordering is safe: {@code ServerPlayer.openMenu} assigns {@code containerMenu} and
 * <em>then</em> posts {@code PlayerContainerEvent.Open}; {@code stillValid} is only consulted
 * in {@code doTick} on the <em>next</em> tick, so the buff is in place before the first
 * {@code canPlayerUse} check. On close, {@code PlayerContainerEvent.Close} fires after
 * {@code stillValid} has already returned false (or been skipped) — revoking then is
 * exactly the semantics we want.
 */
public final class StockTickerBuffListener {

    private static final String STOCK_KEEPER_REQUEST_MENU =
            "com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu";
    /** The other stock ticker menu (categories + payment slot). */
    private static final String STOCK_KEEPER_CATEGORY_MENU =
            "com.simibubi.create.content.logistics.stockTicker.StockKeeperCategoryMenu";

    private StockTickerBuffListener() {}

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (isStockMenu(event) && event.getEntity() instanceof ServerPlayer sp) {
            StockTickerReach.grant(sp);
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (isStockMenu(event) && event.getEntity() instanceof ServerPlayer sp) {
            StockTickerReach.revoke(sp);
        }
    }

    private static boolean isStockMenu(PlayerContainerEvent event) {
        if (event.getContainer() == null) {
            return false;
        }
        String name = event.getContainer().getClass().getName();
        return STOCK_KEEPER_REQUEST_MENU.equals(name) || STOCK_KEEPER_CATEGORY_MENU.equals(name);
    }
}

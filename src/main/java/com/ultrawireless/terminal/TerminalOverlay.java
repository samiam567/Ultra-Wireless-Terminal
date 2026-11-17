package com.ultrawireless.terminal;

import java.util.List;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Client-side coordinator for the terminal-selection flow. The old version also painted a
 * column of switch buttons beside every open Tom's Storage terminal; those are gone by
 * request — terminal switching now happens entirely through the {@link
 * TerminalSelectorScreen} (opened with the M keybind) or the next/previous cycle keybinds.
 *
 * <p>What remains here:
 * <ul>
 *   <li>{@link #openAsLayer} — the one place that knows how to put a screen on top of
 *       whatever is showing (world vs. an open container) without stranding the mouse.</li>
 *   <li>{@link #update} — turns a server {@link TerminalListPacket} into the selector GUI
 *       when one was requested, or refreshes an already-open selector.</li>
 *   <li>{@link ActivationRetry} — a watchdog that re-sends a switch when its replacement
 *       GUI fails to appear. Pure state machine, unit-tested.</li>
 * </ul>
 */
public final class TerminalOverlay {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Latest server-pushed list (client main thread only). */
    private static TerminalListPacket lastList = new TerminalListPacket(List.of());

    /** True between selector-keybind and the selector screen actually opening. */
    private static boolean selectorRequested;
    /** Ticks the selector request has been waiting; expires so it can't pop up late. */
    private static int selectorRequestAge;
    static final int SELECTOR_REQUEST_TIMEOUT_TICKS = 100;

    private TerminalOverlay() {}

    /**
     * Called (client main thread) when {@link TerminalListPacket} arrives: cache it, and
     * open {@link TerminalSelectorScreen} as a GUI layer if the packet was requested via
     * the selector keybind. Otherwise it only refreshes an already-open selector.
     */
    public static void update(TerminalListPacket packet) {
        lastList = packet;
        int inRange = 0;
        for (TerminalInfo info : packet.terminals()) {
            if (info.inRange()) {
                inRange++;
            }
        }
        LOGGER.info("[UWT2] client received terminal list: {} bound, {} in range",
                packet.terminals().size(), inRange);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (selectorRequested) {
            selectorRequested = false;
            openAsLayer(new TerminalSelectorScreen(packet.terminals()));
        } else if (mc.screen instanceof TerminalSelectorScreen current) {
            // e.g. a refused switch pushed a corrected list: refresh in place (pop/push
            // would churn the layer stack and flicker)
            current.setTerminals(packet.terminals());
        }
    }

    /**
     * Show a screen on top of whatever is up. From the world this must use
     * {@code setScreen} (it releases the grabbed mouse; {@code pushGuiLayer} does not,
     * which would leave the cursor unusable in our GUI). From a screen it is pushed as a
     * NeoForge GUI layer, so closing pops back and the server-side menu stays open.
     */
    public static void openAsLayer(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(screen);
        } else if (!(mc.screen instanceof TerminalSelectorScreen)
                && !(mc.screen instanceof StockSummaryScreen)) {
            mc.pushGuiLayer(screen);
        }
        // already showing one of our screens: keep it (update paths handle content)
    }

    static TerminalListPacket lastList() {
        return lastList;
    }

    /** The M keybind sends {@link OpenSelectorPacket} then expects the GUI next. */
    public static void requestSelector() {
        selectorRequested = true;
        selectorRequestAge = 0;
        UltraWirelessTerminalNetworkHandler.sendOpenSelector();
    }

    /**
     * A switch was requested from the selector screen: start the watchdog (in case the
     * replacement GUI doesn't open) and ask the server to activate + open that terminal.
     */
    public static void beginActivate(TerminalInfo info) {
        if (info == null || !info.inRange() || info.active()) {
            return;
        }
        LOGGER.info("[UWT2] selector switch -> {}", info.label());
        RETRY.begin(info);
        UltraWirelessTerminalNetworkHandler.sendActivate(
                info.dimName(), info.x(), info.y(), info.z());
    }

    static void clear() {
        lastList = new TerminalListPacket(List.of());
        selectorRequested = false;
    }

    /** Compact "overworld 12/64/-8" (namespace stripped); used by the selector rows. */
    static String shortLabel(TerminalInfo info) {
        String dim = info.dimName();
        int colon = dim.indexOf(':');
        if (colon >= 0) {
            dim = dim.substring(colon + 1);
        }
        return dim + " " + info.x() + "/" + info.y() + "/" + info.z();
    }

    // =================== lost-switch watchdog ===================

    /** A requested switch is waiting for its replacement GUI to appear. */
    static final ActivationRetry RETRY = new ActivationRetry();

    /**
     * A switch sends {@link ActivatePacket}; the server closes the current menu and opens
     * the new terminal's menu. If the open is lost (concurrent screen teardown, packet
     * timing), the player is dumped to whatever was underneath and the click looks dead.
     * This watchdog re-sends the activation when no container GUI re-opened in time, up to
     * {@link ActivationRetry#MAX_ATTEMPTS} attempts. Pure state machine, unit-tested.
     */
    static final class ActivationRetry {
        static final int DEADLINE_TICKS = 20;
        static final int MAX_ATTEMPTS = 2;

        /** What the caller should do on the deadline tick. */
        enum Outcome { WAIT, RETRY, GIVE_UP }

        private TerminalInfo pending;
        private int ticksLeft;
        private int attempts;

        void begin(TerminalInfo info) {
            pending = info;
            ticksLeft = DEADLINE_TICKS;
            attempts = 1;
        }

        /** The expected GUI opened (or the player left the flow): stop watching. */
        void cancel() {
            pending = null;
            ticksLeft = 0;
            attempts = 0;
        }

        boolean pending() {
            return pending != null;
        }

        TerminalInfo pendingInfo() {
            return pending;
        }

        /** Call once per client tick while {@link #pending()}. */
        Outcome onTick(boolean containerScreenOpen) {
            if (pending == null) {
                return Outcome.GIVE_UP;
            }
            if (containerScreenOpen) {
                cancel();
                return Outcome.WAIT; // landed
            }
            if (--ticksLeft > 0) {
                return Outcome.WAIT;
            }
            if (attempts >= MAX_ATTEMPTS) {
                cancel();
                return Outcome.GIVE_UP;
            }
            attempts++;
            ticksLeft = DEADLINE_TICKS;
            return Outcome.RETRY;
        }
    }

    /** Drive the watchdog + selector-request expiry once per client tick. */
    public static void tickRetry() {
        if (selectorRequested && ++selectorRequestAge > SELECTOR_REQUEST_TIMEOUT_TICKS) {
            // server never answered (no terminal in inventory, packet lost): don't let
            // an unrelated later list pop the GUI open
            selectorRequested = false;
        }
        if (!RETRY.pending()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean containerOpen = mc.screen instanceof AbstractContainerScreen<?>
                || mc.screen instanceof TerminalSelectorScreen;
        switch (RETRY.onTick(containerOpen)) {
            case RETRY -> {
                TerminalInfo info = RETRY.pendingInfo();
                if (info != null) {
                    LOGGER.info("[UWT2] activation did not land, re-sending switch -> {}", info.label());
                    UltraWirelessTerminalNetworkHandler.sendActivate(
                            info.dimName(), info.x(), info.y(), info.z());
                }
            }
            case GIVE_UP -> LOGGER.info("[UWT2] gave up waiting for terminal GUI to open");
            default -> {}
        }
    }
}

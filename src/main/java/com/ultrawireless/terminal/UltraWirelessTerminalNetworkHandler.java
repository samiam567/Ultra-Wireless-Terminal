package com.ultrawireless.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.tom.storagemod.components.WorldPos;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network handler for the single-bound-terminal (advanced wireless terminal) design.
 *
 * <p>Unlike the old combined view, this opens <b>one</b> Tom's Storage terminal at a time
 * via Tom's Storage's own block interaction ({@code useWithoutItem}), and overlays
 * selector buttons on that GUI (see {@link TerminalOverlay}) to switch which bound
 * in-range terminal is active.
 *
 * <p>Flow:
 * <ul>
 *   <li>{@link #openActiveTerminal} (right-click in air / Tom's keybind): opens the
 *       currently active terminal, or, when it is out of range / gone, automatically
 *       selects and opens the nearest in-range terminal (making it active).
 *   <li>{@link #handleOpenSelector} (M keybind): replies with {@link TerminalListPacket}
 *       so the client overlay can show a button per bound terminal.
 *   <li>{@link #handleActivate} (overlay button click): sets the clicked terminal active
 *       (only while bound + in range) and opens it; the terminal GUI stays open.
 *   <li>{@link #handleRefresh}: the overlay re-requests the list when the GUI opens and
 *       after a switch, so the buttons follow the real in-range state.
 * </ul>
 */
public final class UltraWirelessTerminalNetworkHandler {

    private UltraWirelessTerminalNetworkHandler() {}

    public static void onPayloadRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(OpenSelectorPacket.TYPE, OpenSelectorPacket.CODEC,
                UltraWirelessTerminalNetworkHandler::handleOpenSelector);
        registrar.playToServer(OpenActivePacket.TYPE, OpenActivePacket.CODEC,
                UltraWirelessTerminalNetworkHandler::handleOpenActive);
        registrar.playToServer(ActivatePacket.TYPE, ActivatePacket.CODEC,
                UltraWirelessTerminalNetworkHandler::handleActivate);
        registrar.playToServer(RenamePacket.TYPE, RenamePacket.CODEC,
                UltraWirelessTerminalNetworkHandler::handleRename);
        registrar.playToServer(ClipboardSavePacket.TYPE, ClipboardSavePacket.CODEC,
                UltraWirelessTerminalNetworkHandler::handleSaveClipboard);
        registrar.playToServer(RefreshListPacket.TYPE, RefreshListPacket.CODEC,
                UltraWirelessTerminalNetworkHandler::handleRefresh);
        registrar.playToServer(CyclePacket.TYPE, CyclePacket.CODEC,
                UltraWirelessTerminalNetworkHandler::handleCycle);
        registrar.playToServer(UltraWirelessTerminalPacket.OPEN_STOCK_TICKER_ID,
                UltraWirelessTerminalPacket.OPEN_STOCK_TICKER_CODEC,
                UltraWirelessTerminalNetworkHandler::handleOpenStockTickerServer);
        registrar.playToClient(TerminalListPacket.TYPE, TerminalListPacket.CODEC,
                UltraWirelessTerminalNetworkHandler::handleTerminalList);
        registrar.playToClient(StockSummaryPacket.TYPE, StockSummaryPacket.CODEC,
                UltraWirelessTerminalNetworkHandler::handleStockSummary);
    }

    // ======================= client -> server senders =======================

    /** Request the bound-terminal list and open the overlay (M keybind). */
    public static void sendOpenSelector() {
        PacketDistributor.sendToServer(new OpenSelectorPacket());
    }

    /** Request opening the active/nearest terminal (right-click in air). */
    public static void sendOpenActive() {
        PacketDistributor.sendToServer(new OpenActivePacket());
    }

    /** Ask the server to activate a specific bound terminal (overlay button click). */
    public static void sendActivate(String dimName, int x, int y, int z) {
        PacketDistributor.sendToServer(new ActivatePacket(dimName, x, y, z));
    }

    /** Ask the server to (re)name a bound terminal. Blank name clears the custom name. */
    public static void sendRename(String dimName, int x, int y, int z, String name) {
        PacketDistributor.sendToServer(new RenamePacket(dimName, x, y, z, name));
    }

    /** Client asks the server to persist edited clipboard content onto the terminal item. */
    public static void sendSaveClipboard(net.minecraft.nbt.CompoundTag tag) {
        PacketDistributor.sendToServer(new ClipboardSavePacket(tag));
    }

    /** Re-sync the overlay's button list (when the terminal GUI opens). */
    public static void sendRefresh() {
        PacketDistributor.sendToServer(new RefreshListPacket());
    }

    /** Cycle the active terminal (fallback keybinds; direction ±1). */
    public static void sendCycle(int direction) {
        PacketDistributor.sendToServer(new CyclePacket(direction));
    }

    /** Request the closest bound Create stock ticker (keybind N). */
    public static void sendOpenStockTicker() {
        PacketDistributor.sendToServer(new UltraWirelessTerminalPacket());
    }

    // ======================= client-side handler (s -> c) =======================

    private static void handleTerminalList(TerminalListPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> TerminalOverlay.update(packet));
    }

    private static void handleStockSummary(StockSummaryPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> TerminalOverlay.openAsLayer(new StockSummaryScreen(packet.lines())));
    }

    // ======================= server-side handlers (c -> s) =======================

    private static void handleOpenSelector(OpenSelectorPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = findTerminal(player);
            if (stack.isEmpty()) {
                player.displayClientMessage(
                        Component.literal("No Ultra Wireless Terminal found in inventory"), true);
                return;
            }
            sendTerminalList(player, stack);
        });
    }

    private static void handleRefresh(RefreshListPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = findTerminal(player);
            if (stack.isEmpty()) {
                return;
            }
            sendTerminalList(player, stack);
        });
    }

    private static void handleOpenActive(OpenActivePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = findTerminal(player);
            if (stack.isEmpty()) {
                player.displayClientMessage(
                        Component.literal("No Ultra Wireless Terminal found in inventory"), true);
                return;
            }
            openActiveTerminal(player, stack);
        });
    }

    /**
     * Overlay button click: set the clicked terminal active (only while bound + in
     * range) and open it. After switching, Tom's Storage's terminal GUI is re-opened at
     * the new terminal so the player keeps browsing storage without leaving the screen.
     */
    private static void handleActivate(ActivatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = findTerminal(player);
            if (stack.isEmpty()) {
                return;
            }
            WorldPos clicked;
            try {
                clicked = packet.worldPos();
            } catch (RuntimeException e) {
                return; // malformed dimension id
            }
            List<WorldPos> bound = stack.getOrDefault(
                    UltraWirelessTerminalMod.BOUND_TERMINALS.get(), List.of());
            InRangeContext ctx = InRangeChecker.contextFor(player,
                    UltraWirelessTerminalItem.RANGE);
            WorldPos newActive = Selection.requestSwitch(bound, clicked,
                    wp -> isUsable(player, ctx, wp));
            if (newActive == null) {
                // refused (not bound or out of range) -> keep current selection, resync list
                player.displayClientMessage(
                        Component.translatable("chat.ultra_wireless_terminal_2.terminal_out_of_range"), true);
                sendTerminalList(player, stack);
                return;
            }
            stack.set(UltraWirelessTerminalMod.ACTIVE_TERMINAL.get(), newActive);
            boolean opened = openTerminalAt(player, newActive);
            if (!opened) {
                // selection persisted (opens next time in range) but the GUI did not open now
                player.displayClientMessage(
                        Component.translatable("chat.ultra_wireless_terminal_2.terminal_unopenable"), true);
            }
            sendTerminalList(player, stack);
        });
    }

    /**
     * Selector row's Save button: stores (or clears, when blank) a display name for one
     * bound terminal in the {@code TERMINAL_NAMES} component. Renaming an unlinked position
     * is silently ignored so the map never accumulates stale entries. A fresh list is
     * pushed back so every open selector re-renders the row with the new name.
     */
    private static void handleRename(RenamePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = findTerminal(player);
            if (stack.isEmpty()) {
                return;
            }
            WorldPos target;
            try {
                target = packet.worldPos();
            } catch (RuntimeException e) {
                return;
            }
            List<WorldPos> bound = stack.getOrDefault(
                    UltraWirelessTerminalMod.BOUND_TERMINALS.get(), List.of());
            if (!bound.contains(target)) {
                return; // refuse names for unlinked positions
            }
            String key = TerminalNameKey.of(target);
            String value = packet.name() == null ? "" : packet.name().trim();
            Map<String, String> current = new java.util.HashMap<>(stack.getOrDefault(
                    UltraWirelessTerminalMod.TERMINAL_NAMES.get(), Map.of()));
            if (value.isEmpty()) {
                current.remove(key);
            } else {
                if (value.length() > 64) {
                    value = value.substring(0, 64);
                }
                current.put(key, value);
            }
            if (current.isEmpty()) {
                stack.remove(UltraWirelessTerminalMod.TERMINAL_NAMES.get());
            } else {
                stack.set(UltraWirelessTerminalMod.TERMINAL_NAMES.get(), current);
            }
            sendTerminalList(player, stack);
        });
    }

    /** Clipboard button in the selector: push the stored entries back to open the editor. */
    private static void handleSaveClipboard(ClipboardSavePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = findTerminal(player);
            if (stack.isEmpty()) {
                return;
            }
            net.minecraft.nbt.CompoundTag tag = packet.tag();
            if (tag == null || tag.isEmpty()) {
                stack.remove(UltraWirelessTerminalMod.CLIPBOARD_TAG.get());
            } else {
                stack.set(UltraWirelessTerminalMod.CLIPBOARD_TAG.get(), tag);
            }
            // If Create is present, also mirror into AllDataComponents.CLIPBOARD_CONTENT so
            // Create's own clipboard readers (e.g. AddressEditBoxHelper) see our data too.
            if (StockTickerProxy.isCreateLoaded()) {
                try {
                    com.simibubi.create.content.equipment.clipboard.ClipboardContent content =
                            com.simibubi.create.content.equipment.clipboard.ClipboardContent.CODEC
                                    .parse(net.minecraft.nbt.NbtOps.INSTANCE, tag)
                                    .result().orElse(null);
                    if (content == null) {
                        stack.remove(com.simibubi.create.AllDataComponents.CLIPBOARD_CONTENT);
                    } else {
                        stack.set(com.simibubi.create.AllDataComponents.CLIPBOARD_CONTENT, content);
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static void handleOpenStockTickerServer(UltraWirelessTerminalPacket packet,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }
            ItemStack stack = findTerminal(sender);
            if (stack.isEmpty()) {
                sender.displayClientMessage(
                        Component.literal("No Ultra Wireless Terminal found in inventory"), true);
                return;
            }
            // nearest-only by design (unchanged from v1); internally: open Create's GUI
            // when the ticker is within Create's reach, otherwise ship the summary for
            // our own StockSummaryScreen (Create's menu would auto-close beyond reach)
            UltraWirelessTerminalItem.activateStockTicker(sender, stack);
        });
    }



    // ======================= server-side open logic =======================

    /**
     * Opens the currently active terminal; when it is out of range / unlinked / gone,
     * automatically selects the nearest in-range terminal (persisting it as active) and
     * opens that instead. Messages the player when nothing is reachable.
     */
    public static void openActiveTerminal(ServerPlayer player, ItemStack stack) {
        List<WorldPos> bound = stack.getOrDefault(
                UltraWirelessTerminalMod.BOUND_TERMINALS.get(), List.of());
        WorldPos active = stack.get(UltraWirelessTerminalMod.ACTIVE_TERMINAL.get());
        InRangeContext ctx = InRangeChecker.contextFor(player, UltraWirelessTerminalItem.RANGE);

        WorldPos toOpen = Selection.resolveOnOpen(bound, active,
                wp -> isUsable(player, ctx, wp),
                player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ());

        if (toOpen == null) {
            player.displayClientMessage(
                    Component.literal("No linked terminals in range"), true);
            return;
        }
        if (!toOpen.equals(active)) {
            stack.set(UltraWirelessTerminalMod.ACTIVE_TERMINAL.get(), toOpen);
        }
        openTerminalAt(player, toOpen);
    }

    /**
     * Fallback cycle keybind: move the active terminal to the next/previous in-range
     * bound terminal and open it. Works with or without a terminal GUI currently open
     * (without one it simply starts one), so it never depends on overlay rendering.
     */
    private static void handleCycle(CyclePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = findTerminal(player);
            if (stack.isEmpty()) {
                player.displayClientMessage(
                        Component.literal("No Ultra Wireless Terminal found in inventory"), true);
                return;
            }
            List<WorldPos> bound = stack.getOrDefault(
                    UltraWirelessTerminalMod.BOUND_TERMINALS.get(), List.of());
            WorldPos active = stack.get(UltraWirelessTerminalMod.ACTIVE_TERMINAL.get());
            InRangeContext ctx = InRangeChecker.contextFor(player,
                    UltraWirelessTerminalItem.RANGE);
            WorldPos next = Selection.cycle(bound, active,
                    packet.direction() >= 0 ? 1 : -1,
                    wp -> isUsable(player, ctx, wp));
            if (next == null) {
                player.displayClientMessage(
                        Component.translatable("chat.ultra_wireless_terminal_2.no_terminals_in_range"), true);
                return;
            }
            stack.set(UltraWirelessTerminalMod.ACTIVE_TERMINAL.get(), next);
            openTerminalAt(player, next);
            sendTerminalList(player, stack);
        });
    }

    /**
     * Opens a specific terminal by delegating to Tom's Storage's own block interaction,
     * which opens Tom's Storage's real terminal GUI (including Tom's own range check).
     *
     * @return true when the open was delegated successfully; false when the target is
     *         unloaded or no longer a remote-activatable terminal
     */
    public static boolean openTerminalAt(ServerPlayer player, WorldPos wp) {
        Level termWorld = player.server.getLevel(wp.dim());
        if (termWorld == null || !termWorld.isLoaded(wp.pos())) {
            player.displayClientMessage(Component.translatable("chat.toms_storage.terminal_not_loaded"), true);
            return false;
        }
        BlockState state = termWorld.getBlockState(wp.pos());
        if (!state.is(UltraWirelessTerminalItem.REMOTE_ACTIVATE)) {
            player.displayClientMessage(Component.translatable("chat.toms_storage.terminal_invalid_block"), true);
            return false;
        }
        BlockHitResult hit = new BlockHitResult(
                new Vec3(wp.pos().getX() + 0.5D, wp.pos().getY() + 0.5D, wp.pos().getZ() + 0.5D),
                Direction.UP, wp.pos(), true);
        state.useWithoutItem(termWorld, player, hit);
        return true;
    }

    /** Send the selector/overlay data (bound terminals + in-range + active flags). */
    public static void sendTerminalList(ServerPlayer player, ItemStack stack) {
        PacketDistributor.sendToPlayer(player, new TerminalListPacket(buildTerminalInfos(player, stack)));
    }

    /** Loaded + still a terminal block + in range (beacon-aware). */
    private static boolean isUsable(ServerPlayer player, InRangeContext ctx, WorldPos wp) {
        return InRangeChecker.isAvailableAndInRange(player, wp, ctx);
    }

    /**
     * Builds a {@link TerminalInfo} for every bound terminal: whether it is currently
     * in range and whether it is the stored active one.
     */
    public static List<TerminalInfo> buildTerminalInfos(ServerPlayer player, ItemStack stack) {
        List<TerminalInfo> out = new ArrayList<>();
        List<WorldPos> bound = stack.getOrDefault(UltraWirelessTerminalMod.BOUND_TERMINALS.get(), List.of());
        if (bound == null || bound.isEmpty()) {
            return out;
        }
        WorldPos active = stack.get(UltraWirelessTerminalMod.ACTIVE_TERMINAL.get());
        InRangeContext ctx = InRangeChecker.contextFor(player, UltraWirelessTerminalItem.RANGE);
        Map<String, String> names = stack.getOrDefault(
                UltraWirelessTerminalMod.TERMINAL_NAMES.get(), Map.of());
        for (WorldPos wp : bound) {
            if (wp == null) {
                continue;
            }
            boolean inRange = isUsable(player, ctx, wp);
            boolean isActive = active != null && active.equals(wp);
            String custom = names.get(TerminalNameKey.of(wp));
            out.add(new TerminalInfo(wp.dim().location().toString(),
                    wp.pos().getX(), wp.pos().getY(), wp.pos().getZ(),
                    inRange, isActive, custom));
        }
        return out;
    }

    // ======================= terminal lookup =======================

    /**
     * Find an ultra wireless terminal anywhere in the player's inventory (main hand,
     * off hand, storage, then curios if present) — matching the original behavior so
     * the keybind works no matter which slot holds the terminal.
     */
    public static ItemStack findTerminal(ServerPlayer player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (isTerminalItem(main)) {
            return main;
        }
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (isTerminalItem(off)) {
            return off;
        }
        var items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            if (isTerminalItem(item)) {
                return item;
            }
        }
        ItemStack curio = findCuriosTerminal(player);
        return curio == null ? ItemStack.EMPTY : curio;
    }

    private static ItemStack findCuriosTerminal(ServerPlayer player) {
        try {
            Class<?> curiosCap = Class.forName("top.theillusivec4.curios.api.CuriosCapability");
            Object inventoryKey = curiosCap.getField("INVENTORY").get(null);
            java.lang.reflect.Method getCapability =
                    player.getClass().getMethod("getCapability", inventoryKey.getClass());
            Object handler = getCapability.invoke(player, inventoryKey);
            if (handler == null) {
                return null;
            }
            java.lang.reflect.Method findCurios = handler.getClass().getMethod("findCurios",
                    java.util.function.Predicate.class);
            for (Object curio : (List<?>) findCurios.invoke(handler,
                    (Predicate<ItemStack>) s -> s.getItem() instanceof UltraWirelessTerminalItem)) {
                ItemStack item = (ItemStack) curio.getClass().getMethod("stack").invoke(curio);
                if (item.getItem() instanceof UltraWirelessTerminalItem) {
                    return item;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        return null;
    }

    private static boolean isTerminalItem(ItemStack stack) {
        return stack.getItem() instanceof UltraWirelessTerminalItem;
    }
}

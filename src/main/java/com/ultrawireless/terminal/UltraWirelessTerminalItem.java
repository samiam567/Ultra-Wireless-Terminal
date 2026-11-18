package com.ultrawireless.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.tom.storagemod.components.WorldPos;
import com.tom.storagemod.item.WirelessTerminal;

/**
 * Ultra Wireless Terminal 2 — like Tom's Storage's <em>advanced</em> wireless terminal,
 * it opens <b>one</b> bound terminal at a time (Tom's Storage's real terminal GUI via the
 * block's own interaction), but it can be linked to many terminals and remembers which
 * one is active:
 *
 * <ul>
 *   <li><b>Right-click in air</b> (or Tom's Storage keybind while held): opens the active
 *       terminal; if it is out of range / unlinked / gone, the nearest in-range terminal
 *       is selected and opened instead (and persisted as active).</li>
 *   <li><b>Selection key (M)</b> (see {@link TerminalSelectorScreen}): pick which bound
 *       terminal is active, or cycle with the (unbound) next/previous keybinds.</li>
 *   <li><b>Sneak + right-click a terminal</b>: link / unlink that terminal.</li>
 *   <li><b>Sneak + right-click in air</b>: clear all terminal links.</li>
 *   <li><b>Stock key (N)</b>: browse the linked Create logistics network's stock from
 *       <em>anywhere</em> — the terminal is itself the ticker, so no ticker block has to
 *       be near (or even loaded). Link it once by sneak + right-clicking a ticker.</li>
 * </ul>
 */
public class UltraWirelessTerminalItem extends Item implements WirelessTerminal {

    public static final TagKey<Block> REMOTE_ACTIVATE =
            BlockTags.create(ResourceLocation.fromNamespaceAndPath("toms_storage", "remote_activate"));

    private static final ResourceLocation STOCK_TICKER_LOCATION =
            ResourceLocation.fromNamespaceAndPath("create", "stock_ticker");

    /** Wireless reach of this item (Tom's Storage's advanced terminal default). */
    public static final int RANGE = 64;

    public UltraWirelessTerminalItem(Item.Properties properties) {
        super(properties);
    }

    public static boolean isStockTicker(Block block) {
        if (block == null) {
            return false;
        }
        Optional<ResourceLocation> loc = block.builtInRegistryHolder().unwrapKey().map(key -> key.location());
        return loc.isPresent() && loc.get().equals(STOCK_TICKER_LOCATION);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext tooltipContext, List<Component> tooltip,
            TooltipFlag tooltipFlag) {
        List<WorldPos> linked = stack.get(UltraWirelessTerminalMod.BOUND_TERMINALS.get());
        int termCount = linked == null ? 0 : linked.size();
        boolean createPresent = isCreateInstalled();
        WorldPos active = stack.get(UltraWirelessTerminalMod.ACTIVE_TERMINAL.get());

        tooltip.add(Component.translatable("tooltip.ultra_wireless_terminal_2.open_terminal_keybind",
                Component.keybind("key.toms_storage.open_terminal").withStyle(ChatFormatting.GREEN)));
        tooltip.add(Component.translatable("tooltip.ultra_wireless_terminal_2.open_selector_keybind",
                Component.keybind("key.ultra_wireless_terminal_2.open_selector").withStyle(ChatFormatting.AQUA)));
        if (createPresent) {
            tooltip.add(Component.translatable("tooltip.ultra_wireless_terminal_2.open_stock_ticker_keybind",
                    Component.keybind("key.ultra_wireless_terminal_2.open_stock_ticker").withStyle(ChatFormatting.GRAY)));
        }
        tooltip.add(Component.literal("Linked terminals: " + termCount).withStyle(ChatFormatting.GRAY));
        if (active != null) {
            tooltip.add(Component.literal("Active: " + active.dim().location().getPath()
                    + " " + active.pos().getX() + ", " + active.pos().getY() + ", " + active.pos().getZ())
                    .withStyle(ChatFormatting.GOLD));
        }
        if (createPresent) {
            WorldPos ticker = stack.get(UltraWirelessTerminalMod.LINKED_STOCK_TICKER.get());
            String network = linkedNetworkLabel(stack);
            String status;
            if (ticker == null) {
                status = "Stock ticker: not linked";
            } else {
                status = "Stock ticker: " + ticker.pos().getX() + ", " + ticker.pos().getY()
                        + ", " + ticker.pos().getZ() + " (" + ticker.dim().location().getPath() + ")"
                        + (network == null ? "" : " net " + network);
            }
            tooltip.add(Component.literal(status)
                    .withStyle(ticker == null ? ChatFormatting.GRAY : ChatFormatting.LIGHT_PURPLE));
        }
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.literal("Sneak + right-click a terminal to link/unlink.").withStyle(ChatFormatting.GRAY));
            if (createPresent) {
                tooltip.add(Component.literal("Sneak + right-click a stock ticker to link/unlink.").withStyle(ChatFormatting.GRAY));
            }
            tooltip.add(Component.literal("Right-click: open the active terminal (nearest if it went out of range).")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Press the selection key to switch the active terminal.")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.toms_storage.hold_shift_for_info")
                    .withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
        }
    }

    /** Short label for the linked network id (first 8 chars), or null when unlinked. */
    private static String linkedNetworkLabel(ItemStack stack) {
        java.util.UUID id = stack.get(UltraWirelessTerminalMod.LINKED_STOCK_NETWORK.get());
        return id == null ? null : id.toString().substring(0, 8);
    }

    private static boolean isCreateInstalled() {
        try {
            Class<?> modList = Class.forName("net.neoforged.fml.ModList");
            Object instance = modList.getMethod("get").invoke(null);
            return (boolean) modList.getMethod("isLoaded", String.class).invoke(instance, "create");
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /**
     * Right-click in air: open the active terminal, auto-selecting the nearest in-range
     * one when the active terminal is unavailable. Sneak clears all terminal links.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level worldIn, Player playerIn, InteractionHand handIn) {
        ItemStack stack = playerIn.getItemInHand(handIn);
        if (playerIn.isShiftKeyDown()) {
            if (!worldIn.isClientSide) {
                stack.set(UltraWirelessTerminalMod.BOUND_TERMINALS.get(), new ArrayList<>());
                stack.remove(UltraWirelessTerminalMod.ACTIVE_TERMINAL.get());
                playerIn.displayClientMessage(Component.literal("Unlinked all terminals"), true);
            }
            return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
        }
        if (!worldIn.isClientSide && playerIn instanceof ServerPlayer serverPlayer) {
            UltraWirelessTerminalNetworkHandler.openActiveTerminal(serverPlayer, stack);
        }
        return new InteractionResultHolder<>(InteractionResult.sidedSuccess(worldIn.isClientSide), stack);
    }

    /** Sneak + right-click a terminal / stock ticker block: link / unlink it. */
    @Override
    public InteractionResult useOn(UseOnContext c) {
        if (!c.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (c.getLevel().isClientSide) {
            return InteractionResult.CONSUME;
        }
        BlockPos pos = c.getClickedPos();
        BlockState state = c.getLevel().getBlockState(pos);
        ItemStack stack = c.getItemInHand();
        Player player = c.getPlayer();
        if (state.is(REMOTE_ACTIVATE)) {
            WorldPos wp = new WorldPos(c.getLevel().dimension(), pos);
            List<WorldPos> current = stack.get(UltraWirelessTerminalMod.BOUND_TERMINALS.get());
            List<WorldPos> list = new ArrayList<>(current == null ? List.of() : current);
            if (list.contains(wp)) {
                list.remove(wp);
                if (player != null) {
                    player.displayClientMessage(Component.literal("Unlinked terminal"), true);
                }
                WorldPos active = stack.get(UltraWirelessTerminalMod.ACTIVE_TERMINAL.get());
                if (active != null && active.equals(wp)) {
                    stack.remove(UltraWirelessTerminalMod.ACTIVE_TERMINAL.get());
                }
            } else {
                list.add(wp);
                if (player != null) {
                    player.displayClientMessage(Component.translatable("chat.toms_storage.terminal_bound"), true);
                }
            }
            stack.set(UltraWirelessTerminalMod.BOUND_TERMINALS.get(), list);
            return InteractionResult.SUCCESS;
        } else if (isStockTicker(state.getBlock())) {
            // remember this ticker's block position (for opening Create's real GUI) AND
            // its logistics-network frequency (for the anywhere-summary fallback). Both
            // live on the item; either alone would be strictly worse.
            WorldPos wp = new WorldPos(c.getLevel().dimension(), pos);
            WorldPos currentPos = stack.get(UltraWirelessTerminalMod.LINKED_STOCK_TICKER.get());
            if (wp.equals(currentPos)) {
                stack.remove(UltraWirelessTerminalMod.LINKED_STOCK_TICKER.get());
                stack.remove(UltraWirelessTerminalMod.LINKED_STOCK_NETWORK.get());
                if (player != null) {
                    player.displayClientMessage(Component.literal("Unlinked stock ticker"), true);
                }
                return InteractionResult.SUCCESS;
            }
            java.util.UUID freq = StockTickerProxy.captureFrequency(
                    (net.minecraft.server.level.ServerLevel) c.getLevel(), pos);
            if (freq == null) {
                if (player != null) {
                    player.displayClientMessage(
                            Component.literal("No Create stock network found here"), true);
                }
                return InteractionResult.SUCCESS;
            }
            stack.set(UltraWirelessTerminalMod.LINKED_STOCK_TICKER.get(), wp);
            stack.set(UltraWirelessTerminalMod.LINKED_STOCK_NETWORK.get(), freq);
            if (player != null) {
                player.displayClientMessage(Component.literal(
                        "Linked to stock network - press the stock key"), true);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * Stock ticker key: open Create's <em>real</em> stock-ticker GUI (with item ordering)
     * when the linked ticker is loaded and reachable (which the {@link StockTickerReach}
     * buff extends well past Create's usual 8 blocks — see that class). When it is too
     * far / unloaded, fall back to our own distance-free summary via the stored network
     * frequency. Either way the terminal works from anywhere as long as the network exists.
     */
    public static void activateStockTicker(ServerPlayer player, ItemStack stack) {
        if (player.level().isClientSide()) {
            return;
        }
        WorldPos tickerPos = stack.get(UltraWirelessTerminalMod.LINKED_STOCK_TICKER.get());
        // Real GUI first: the ticker must be loaded on both sides for Create's menu to
        // resolve its block entity, and within the buffed reach so `stillValid` holds.
        if (tickerPos != null && canReachRealTickerGui(player, tickerPos)) {
            if (StockTickerProxy.openRemote(player, tickerPos)) {
                return;
            }
        }
        java.util.UUID network = stack.get(UltraWirelessTerminalMod.LINKED_STOCK_NETWORK.get());
        if (network == null) {
            player.displayClientMessage(Component.literal(
                    "No stock ticker linked (sneak + right-click a Create stock ticker)"), true);
            return;
        }
        java.util.List<String> summary = StockTickerProxy.networkSummary(network);
        if (summary == null) {
            player.displayClientMessage(
                    Component.literal("Stock network unavailable (Create not loaded?)"), true);
        } else {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    player, new StockSummaryPacket(summary));
        }
    }

    /**
     * Same-dimension + within {@link StockTickerReach#EFFECTIVE_REACH} of the linked
     * ticker's chunk. Cross-dimension always false: Create's menu cannot resolve a block
     * entity from another dimension anyway.
     */
    private static boolean canReachRealTickerGui(ServerPlayer player, WorldPos wp) {
        if (!wp.dim().equals(player.level().dimension())) {
            return false;
        }
        Level lvl = player.server.getLevel(wp.dim());
        if (lvl == null || !lvl.isLoaded(wp.pos())) {
            return false;
        }
        double dx = wp.pos().getX() + 0.5D - player.getX();
        double dy = wp.pos().getY() + 0.5D - player.getY();
        double dz = wp.pos().getZ() + 0.5D - player.getZ();
        double reach = StockTickerReach.EFFECTIVE_REACH;
        return (dx * dx + dy * dy + dz * dz) <= reach * reach;
    }

    /**
     * Picks the closest candidate, preferring the player's dimension (same rule as v1).
     * Null-tolerant, first-wins on ties, tolerates a null player dimension.
     */
    public static WorldPos selectClosest(List<WorldPos> candidates, ResourceKey<Level> playerDim,
            double px, double py, double pz) {
        WorldPos best = null;
        boolean bestSameDim = false;
        double bestDist = Double.MAX_VALUE;
        for (WorldPos wp : candidates) {
            if (wp == null) {
                continue;
            }
            boolean sameDim = playerDim != null && wp.dim().equals(playerDim);
            double dx = wp.pos().getX() + 0.5D - px;
            double dy = wp.pos().getY() + 0.5D - py;
            double dz = wp.pos().getZ() + 0.5D - pz;
            double dist = sameDim ? (dx * dx + dy * dy + dz * dz) : Double.MAX_VALUE;
            if (best == null || (sameDim && !bestSameDim) || (sameDim == bestSameDim && dist < bestDist)) {
                best = wp;
                bestSameDim = sameDim;
                bestDist = dist;
            }
        }
        return best;
    }

    @Override
    public int getRange(Player pl, ItemStack stack) {
        return RANGE;
    }

    /**
     * Called (server-side) by Tom's Storage's keybind/open path for a held wireless
     * terminal: open the active terminal, or the nearest in-range one if unavailable.
     */
    @Override
    public void open(Player sender, ItemStack t) {
        if (sender instanceof ServerPlayer serverPlayer && !sender.level().isClientSide()) {
            UltraWirelessTerminalNetworkHandler.openActiveTerminal(serverPlayer, t);
        }
    }

    @Override
    public boolean canOpen(ItemStack t) {
        return true;
    }
}

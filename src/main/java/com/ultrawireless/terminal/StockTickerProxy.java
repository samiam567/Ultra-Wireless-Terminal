package com.ultrawireless.terminal;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.tom.storagemod.components.WorldPos;

/**
 * Reflective bridge to Create's logistics stock system. Create is an optional dependency:
 * every entry point degrades gracefully (null) when Create is absent, the target is
 * missing, or an internal signature ever changes.
 *
 * <p>The Ultra Wireless Terminal acts as its own stock ticker. Two steps:
 * <ol>
 *   <li>{@link #captureFrequency} — when the player sneak-links a Create stock ticker we
 *       read that ticker's logistics-network id once. Create stores it on the block
 *       entity's {@code behaviour} ({@code LogisticallyLinkedBehaviour.freqId}, a public
 *       {@code UUID}); that id is the whole network, not a single block, so it stays
 *       meaningful after the ticker is broken or left behind.</li>
 *   <li>{@link #networkSummary} — later, from <em>anywhere</em>, read the live inventory of
 *       that network via {@code LogisticsManager.getSummaryOfNetwork(UUID, boolean)}. That
 *       method is static, backed by a server-wide tick cache, and does no reach check, so
 *       no ticker block needs to be near the player (nor even loaded) to browse stock.</li>
 * </ol>
 *
 * <p>This deliberately replaces the earlier mixin/remote-GUI approach: opening Create's
 * own ticker screen requires a ticker block as its menu host and force-closes beyond reach,
 * but the terminal only needs the <em>numbers</em>, which the frequency gives us directly.
 */
public final class StockTickerProxy {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String LOGISTICS_MANAGER =
            "com.simibubi.create.content.logistics.packagerLink.LogisticsManager";

    private static final String STOCK_HANDLER =
            "com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler";

    /** Create's stock ticker block entity (also used by {@link StockTickerReach}). */
    public static final String STOCK_TICKER_BE =
            "com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity";

    private StockTickerProxy() {}

    /**
     * Opens Create's <em>real</em> stock-ticker GUI — the one with item ordering — at the
     * linked ticker by invoking Create's own
     * {@code StockTickerInteractionHandler.interactWithLogisticsManagerAt(player, level, pos)}.
     * Create opens its {@code StockKeeperRequestMenu} and pushes the network summary; the
     * caller is expected to have granted a temporary {@code BLOCK_INTERACTION_RANGE} buff so
     * the menu survives {@code MenuBase.stillValid -> canPlayerUse} from a distance
     * (see {@link StockTickerReach}).
     *
     * @return true when Create opened the GUI; false when Create is absent, the ticker is
     *         missing, or the reflection signature changed (caller should fall back to
     *         {@link #networkSummary})
     */
    public static boolean openRemote(ServerPlayer player, WorldPos wp) {
        if (!isCreateLoaded() || player == null || wp == null) {
            return false;
        }
        Level level = player.server.getLevel(wp.dim());
        if (level == null || !level.isLoaded(wp.pos())) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(wp.pos());
        if (be == null || !STOCK_TICKER_BE.equals(be.getClass().getName())) {
            return false;
        }
        try {
            Class<?> handler = Class.forName(STOCK_HANDLER);
            Object result = handler
                    .getMethod("interactWithLogisticsManagerAt", Player.class, Level.class, BlockPos.class)
                    .invoke(null, player, level, wp.pos());
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.warn("[UWT2] failed to open Create stock ticker at {}", wp.pos(), e);
            return false;
        }
    }

    static boolean isCreateLoaded() {
        try {
            Class<?> modList = Class.forName("net.neoforged.fml.ModList");
            Object instance = modList.getMethod("get").invoke(null);
            return (boolean) modList.getMethod("isLoaded", String.class).invoke(instance, "create");
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /**
     * Reads the logistics-network frequency from the Create stock ticker (or any
     * logistically-linked block) at {@code pos}.
     *
     * @return the network {@code UUID}, or null when Create is absent, the chunk is
     *         unloaded, the block has no behaviour/frequency, or the signature changed
     */
    public static UUID captureFrequency(ServerLevel level, BlockPos pos) {
        if (!isCreateLoaded() || level == null || pos == null) {
            return null;
        }
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }
        try {
            // StockCheckingBlockEntity.behaviour (public), whose public `freqId` field
            // is the logistics network id.
            Field behaviourField = be.getClass().getField("behaviour");
            Object behaviour = behaviourField.get(be);
            if (behaviour == null) {
                return null;
            }
            Field freqField = behaviour.getClass().getField("freqId");
            Object value = freqField.get(behaviour);
            return value instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.warn("[UWT2] failed to read Create logistics frequency at {}", pos, e);
            return null;
        }
    }

    /**
     * Live stock of a logistics network, formatted for {@link StockSummaryScreen}. Reads
     * the (distance-independent) server summary cache Create uses to drive its own GUI.
     *
     * @return display lines (header + one per item), or null when Create is absent or the
     *         read fails. A reachable-but-empty network returns a single "no items" line.
     */
    public static List<String> networkSummary(UUID networkId) {
        if (!isCreateLoaded() || networkId == null) {
            return null;
        }
        try {
            Class<?> manager = Class.forName(LOGISTICS_MANAGER);
            Object summary = manager
                    .getMethod("getSummaryOfNetwork", UUID.class, boolean.class)
                    .invoke(null, networkId, false);
            if (summary == null) {
                return null;
            }
            return formatSummary(summary);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.warn("[UWT2] failed to read Create network summary for {}", networkId, e);
            return null;
        }
    }

    /**
     * {@code InventorySummary.getItemMap()} returns {@code Map<Item, List<BigItemStack>>};
     * {@code BigItemStack} exposes public fields {@code stack} (ItemStack) and {@code count}
     * (long). Any signature change yields a degraded (or null) result.
     */
    private static List<String> formatSummary(Object summary) throws ReflectiveOperationException {
        Object itemMap = summary.getClass().getMethod("getItemMap").invoke(summary);
        if (!(itemMap instanceof Map<?, ?> map) || map.isEmpty()) {
            return List.of("No items in this logistics network yet.");
        }
        List<String> entries = new ArrayList<>();
        long total = 0;
        for (Object listObj : map.values()) {
            if (!(listObj instanceof List<?> stacks)) {
                continue;
            }
            for (Object big : stacks) {
                ItemStack stack = readItemStack(big);
                long count = readCount(big);
                if (stack == null || stack.isEmpty() || count <= 0) {
                    continue;
                }
                total += count;
                entries.add(stack.getItem().getDescription().getString() + " x " + count);
            }
        }
        if (entries.isEmpty()) {
            return List.of("No items in this logistics network yet.");
        }
        List<String> lines = new ArrayList<>(entries.size() + 1);
        lines.add(total + " items across " + map.size() + " types:");
        lines.addAll(entries);
        return lines;
    }

    private static ItemStack readItemStack(Object bigItemStack) {
        try {
            Field f = bigItemStack.getClass().getField("stack");
            Object v = f.get(bigItemStack);
            return v instanceof ItemStack stack ? stack : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static long readCount(Object bigItemStack) {
        try {
            Field f = bigItemStack.getClass().getField("count");
            Object v = f.get(bigItemStack);
            return v instanceof Number n ? n.longValue() : 0L;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0L;
        }
    }
}

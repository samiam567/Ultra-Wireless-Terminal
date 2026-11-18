package com.ultrawireless.terminal;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.content.trains.schedule.DestinationSuggestions;

import net.createmod.catnip.data.IntAttached;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import org.slf4j.Logger;

/**
 * Injects package address entries from the Ultra Wireless Terminal's stored clipboard
 * into Create's stock-ticker address input ({@link AddressEditBox}) and its autocomplete
 * suggestions dropdown ({@link DestinationSuggestions}).
 *
 * <p>Create's own {@code AddressEditBoxHelper} only scans items matching
 * {@code AllBlocks.CLIPBOARD} and nearby placed {@code ClipboardBlockEntity} blocks.
 * Because the Ultra Wireless Terminal is its own item type, Create's scanner skips it.
 * This listener catches {@link ScreenEvent.Init.Post} when the stock-ticker GUI (or any
 * screen containing an {@link AddressEditBox}) initializes, extracts all valid package
 * addresses from the terminal's stored clipboard, populates them into the address box's
 * {@code destinationSuggestions.viableStations} list, and pre-fills the address box if it
 * is currently blank.
 */
public final class CreateStockTickerAutofill {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Pure representation of a clipboard entry (decoupled from Minecraft bootstrap for testing). */
    public record RawClipboardEntry(boolean checked, String text) {}

    public CreateStockTickerAutofill() {
    }

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen == null) {
            return;
        }

        List<AddressEditBox> boxes = findAddressBoxes(screen);
        if (boxes.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        ItemStack terminal = findTerminalStack(player);
        if (terminal.isEmpty()) {
            return;
        }

        List<String> addresses = extractAddresses(terminal);
        if (addresses.isEmpty()) {
            return;
        }

        for (AddressEditBox box : boxes) {
            populateSuggestions(box, addresses);
            if (box.getValue().isBlank()) {
                box.setValue(addresses.get(0));
            }
        }
    }

    /**
     * Extracts all package addresses from the terminal's stored clipboard.
     */
    public static List<String> extractAddresses(ItemStack terminal) {
        if (terminal == null || terminal.isEmpty()) {
            return List.of();
        }
        ClipboardContent content = readClipboardContent(terminal);
        return extractAddresses(content);
    }

    /**
     * Extracts addresses from a parsed {@link ClipboardContent}.
     */
    public static List<String> extractAddresses(ClipboardContent content) {
        if (content == null) {
            return List.of();
        }
        List<List<ClipboardEntry>> pages = ClipboardEntry.readAll(content);
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }

        List<RawClipboardEntry> rawEntries = new ArrayList<>();
        for (List<ClipboardEntry> page : pages) {
            if (page == null) {
                continue;
            }
            for (ClipboardEntry entry : page) {
                if (entry == null || entry.text == null) {
                    continue;
                }
                rawEntries.add(new RawClipboardEntry(entry.checked, entry.text.getString()));
            }
        }
        return extractAddressesFromEntries(rawEntries);
    }

    /**
     * Pure selection rule for clipboard addresses:
     * <ul>
     *   <li>Prefers entries starting with {@code #} (Create's official package address designation,
     *       e.g. {@code #Storage Bay} &rarr; {@code Storage Bay}).</li>
     *   <li>Skips checked entries and blank / hash-only strings.</li>
     *   <li>Deduplicates while preserving order.</li>
     *   <li>Falls back to non-blank unchecked text if no {@code #} entries exist.</li>
     * </ul>
     */
    public static List<String> extractAddressesFromEntries(List<RawClipboardEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        List<String> addresses = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 1. Look for entries starting with '#'
        for (RawClipboardEntry entry : entries) {
            if (entry == null || entry.checked() || entry.text() == null) {
                continue;
            }
            String text = entry.text().trim();
            if (text.startsWith("#") && text.length() > 1) {
                String address = text.substring(1).trim();
                if (!address.isBlank() && seen.add(address)) {
                    addresses.add(address);
                }
            }
        }

        // 2. Fallback: if no '#' entries were found at all, treat non-blank unchecked lines as addresses
        if (addresses.isEmpty()) {
            for (RawClipboardEntry entry : entries) {
                if (entry == null || entry.checked() || entry.text() == null) {
                    continue;
                }
                String text = entry.text().trim();
                if (!text.isBlank() && seen.add(text)) {
                    addresses.add(text);
                }
            }
        }

        return addresses;
    }

    /**
     * Injects address suggestions into the given {@link AddressEditBox}'s internal
     * {@link DestinationSuggestions#viableStations} list via reflection.
     *
     * @return true if new suggestions were successfully added
     */
    public static boolean populateSuggestions(AddressEditBox box, List<String> addresses) {
        if (box == null || addresses == null || addresses.isEmpty()) {
            return false;
        }
        try {
            Field destField = AddressEditBox.class.getDeclaredField("destinationSuggestions");
            destField.setAccessible(true);
            Object destSuggestions = destField.get(box);
            if (destSuggestions == null) {
                return false;
            }

            Field stationsField = DestinationSuggestions.class.getDeclaredField("viableStations");
            stationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<IntAttached<String>> stations = (List<IntAttached<String>>) stationsField.get(destSuggestions);
            if (stations == null) {
                return false;
            }

            Set<String> existing = new HashSet<>();
            for (IntAttached<String> st : stations) {
                if (st != null && st.getValue() != null) {
                    existing.add(st.getValue());
                }
            }

            boolean added = false;
            for (String addr : addresses) {
                if (addr != null && !addr.isBlank() && existing.add(addr)) {
                    stations.add(IntAttached.withZero(addr));
                    added = true;
                }
            }

            if (added) {
                // Reset the cached 'previous' query string in DestinationSuggestions so
                // that updateCommandInfo() forces a re-evaluation of current suggestions.
                try {
                    Field prevField = DestinationSuggestions.class.getDeclaredField("previous");
                    prevField.setAccessible(true);
                    prevField.set(destSuggestions, "<>");
                } catch (Exception ignored) {
                }
                if (destSuggestions instanceof DestinationSuggestions ds) {
                    ds.updateCommandInfo();
                }
            }
            return added;
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[UWT2] failed to inject autofill suggestions into AddressEditBox", e);
            return false;
        }
    }

    /** Finds any {@link AddressEditBox} instances present on the screen. */
    public static List<AddressEditBox> findAddressBoxes(Screen screen) {
        if (screen == null) {
            return List.of();
        }
        List<AddressEditBox> boxes = new ArrayList<>();
        if (screen instanceof StockKeeperRequestScreen req && req.addressBox != null) {
            boxes.add(req.addressBox);
        }
        for (var child : screen.children()) {
            if (child instanceof AddressEditBox box && !boxes.contains(box)) {
                boxes.add(box);
            }
        }
        return boxes;
    }

    /**
     * Resolves the {@link ClipboardContent} stored on the terminal item, parsing from the
     * persistent {@code CLIPBOARD_TAG} data component if necessary.
     */
    public static ClipboardContent readClipboardContent(ItemStack terminal) {
        if (terminal == null || terminal.isEmpty()) {
            return null;
        }
        if (terminal.has(AllDataComponents.CLIPBOARD_CONTENT)) {
            ClipboardContent content = terminal.get(AllDataComponents.CLIPBOARD_CONTENT);
            if (content != null) {
                return content;
            }
        }
        CompoundTag tag = terminal.get(UltraWirelessTerminalMod.CLIPBOARD_TAG.get());
        if (tag != null && !tag.isEmpty()) {
            try {
                ClipboardContent parsed = ClipboardContent.CODEC.parse(NbtOps.INSTANCE, tag)
                        .result().orElse(null);
                if (parsed != null) {
                    terminal.set(AllDataComponents.CLIPBOARD_CONTENT, parsed);
                    return parsed;
                }
            } catch (Exception e) {
                LOGGER.debug("[UWT2] failed to parse clipboard tag", e);
            }
        }
        return null;
    }

    /**
     * Searches player hands, main inventory, and Curios belt slots for the terminal item.
     */
    public static ItemStack findTerminalStack(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main.getItem() instanceof UltraWirelessTerminalItem) {
            return main;
        }
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.getItem() instanceof UltraWirelessTerminalItem) {
            return off;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item.getItem() instanceof UltraWirelessTerminalItem) {
                return item;
            }
        }
        ItemStack curio = findCuriosTerminal(player);
        return curio == null ? ItemStack.EMPTY : curio;
    }

    private static ItemStack findCuriosTerminal(Player player) {
        try {
            Class<?> curiosCap = Class.forName("top.theillusivec4.curios.api.CuriosCapability");
            Object inventoryKey = curiosCap.getField("INVENTORY").get(null);
            java.lang.reflect.Method getCapability =
                    player.getClass().getMethod("getCapability", inventoryKey.getClass());
            Object lazyOpt = getCapability.invoke(player, inventoryKey);
            if (lazyOpt instanceof java.util.Optional<?> opt && opt.isPresent()) {
                Object handler = opt.get();
                java.lang.reflect.Method findCurio = handler.getClass().getMethod("findFirstCurio",
                        net.minecraft.world.item.Item.class);
                Object result = findCurio.invoke(handler, UltraWirelessTerminalMod.ULTRA_WIRELESS_TERMINAL.get());
                if (result instanceof java.util.Optional<?> rOpt && rOpt.isPresent()) {
                    Object slotResult = rOpt.get();
                    java.lang.reflect.Method stackMethod = slotResult.getClass().getMethod("stack");
                    return (ItemStack) stackMethod.invoke(slotResult);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}

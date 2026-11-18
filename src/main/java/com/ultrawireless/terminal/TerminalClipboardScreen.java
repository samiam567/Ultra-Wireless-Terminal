package com.ultrawireless.terminal;

import java.lang.reflect.Field;
import java.util.List;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides.ClipboardType;
import com.simibubi.create.content.equipment.clipboard.ClipboardScreen;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

/**
 * Child class of Create's {@link ClipboardScreen} adapted for the Ultra Wireless Terminal.
 *
 * <p>Rather than saving to a hotbar slot or block entity via Create's internal packet
 * (which requires the item to be {@code AllBlocks.CLIPBOARD}), this screen:
 * <ul>
 *   <li>Reads {@link ClipboardContent} directly from the Ultra Wireless Terminal's
 *       components or its {@link UltraWirelessTerminalMod#CLIPBOARD_TAG}.</li>
 *   <li>Renders Create's authentic clipboard GUI (pages, checkboxes, icons, etc.).</li>
 *   <li>On close, serializes the updated {@link ClipboardContent} and sends a
 *       {@link ClipboardSavePacket} to the server to persist it inside the terminal item.</li>
 * </ul>
 */
public class TerminalClipboardScreen extends ClipboardScreen {

    private static final Field PAGES_FIELD;
    private static final Field CURRENT_ENTRIES_FIELD;

    static {
        Field pages = null;
        Field cur = null;
        try {
            pages = ClipboardScreen.class.getDeclaredField("pages");
            pages.setAccessible(true);
            cur = ClipboardScreen.class.getDeclaredField("currentEntries");
            cur.setAccessible(true);
        } catch (ReflectiveOperationException ignored) {
        }
        PAGES_FIELD = pages;
        CURRENT_ENTRIES_FIELD = cur;
    }

    private final ItemStack terminalStack;

    public TerminalClipboardScreen(ItemStack terminalStack) {
        super(0, buildComponentMap(terminalStack), null);
        this.terminalStack = terminalStack;
    }

    private static DataComponentMap buildComponentMap(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return DataComponentMap.EMPTY;
        }
        if (stack.has(AllDataComponents.CLIPBOARD_CONTENT)) {
            return stack.getComponents();
        }
        CompoundTag tag = stack.get(UltraWirelessTerminalMod.CLIPBOARD_TAG.get());
        if (tag != null && !tag.isEmpty()) {
            try {
                ClipboardContent parsed = ClipboardContent.CODEC.parse(NbtOps.INSTANCE, tag)
                        .result().orElse(null);
                if (parsed != null) {
                    PatchedDataComponentMap map = new PatchedDataComponentMap(stack.getComponents());
                    map.set(AllDataComponents.CLIPBOARD_CONTENT, parsed);
                    return map;
                }
            } catch (Exception ignored) {
            }
        }
        return stack.getComponents();
    }

    @SuppressWarnings("unchecked")
    private List<List<ClipboardEntry>> readPages() {
        if (PAGES_FIELD != null) {
            try {
                return (List<List<ClipboardEntry>>) PAGES_FIELD.get(this);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<ClipboardEntry> readCurrentEntries() {
        if (CURRENT_ENTRIES_FIELD != null) {
            try {
                return (List<ClipboardEntry>) CURRENT_ENTRIES_FIELD.get(this);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return List.of();
    }

    @Override
    public void removed() {
        List<List<ClipboardEntry>> pages = readPages();
        List<ClipboardEntry> currentEntries = readCurrentEntries();

        pages.forEach(list -> list.removeIf(ce -> ce.text.getString().isBlank()));
        pages.removeIf(List::isEmpty);

        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i) == currentEntries) {
                content = content.setPreviouslyOpenedPage(i);
            }
        }

        content = content.setPages(pages);
        content = content.setType(ClipboardType.WRITTEN);

        ClipboardContent toSend = pages.isEmpty() ? null : content;

        // Update local client stack immediately
        if (terminalStack != null && !terminalStack.isEmpty()) {
            if (toSend == null) {
                terminalStack.remove(AllDataComponents.CLIPBOARD_CONTENT);
                terminalStack.remove(UltraWirelessTerminalMod.CLIPBOARD_TAG.get());
            } else {
                terminalStack.set(AllDataComponents.CLIPBOARD_CONTENT, toSend);
                try {
                    CompoundTag tag = (CompoundTag) ClipboardContent.CODEC.encodeStart(NbtOps.INSTANCE, toSend)
                            .result().orElse(null);
                    if (tag != null) {
                        terminalStack.set(UltraWirelessTerminalMod.CLIPBOARD_TAG.get(), tag);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // Send to server to persist
        CompoundTag tagToSend = null;
        if (toSend != null) {
            try {
                tagToSend = (CompoundTag) ClipboardContent.CODEC.encodeStart(NbtOps.INSTANCE, toSend)
                        .result().orElse(null);
            } catch (Exception ignored) {
            }
        }
        UltraWirelessTerminalNetworkHandler.sendSaveClipboard(tagToSend);
    }
}

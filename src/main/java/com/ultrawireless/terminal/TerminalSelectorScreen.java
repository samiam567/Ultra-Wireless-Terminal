package com.ultrawireless.terminal;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The terminal selection GUI: a centred list with one button per bound terminal. Opened by
 * the selector keybind (M) from the world <em>or</em> from inside an open terminal, and
 * re-opened after a switch so the player can chain selections.
 *
 * <p>Each row also has a small <em>Name</em> button: clicking it swaps the row into edit
 * mode (row button → text field, name button → Save). Saving sends a {@link RenamePacket};
 * a fresh list arrives from the server so the new label shows immediately. Only one row is
 * edited at a time; Escape while editing cancels rather than closing the screen.
 *
 * <p>Why a real {@link Screen}: the previous design painted buttons onto the open container
 * screen, which needs that screen's panel bounds ({@code leftPos}/{@code topPos} …). Those
 * fields are {@code protected} and obfuscated in a production client, so a reflective
 * lookup by mojmap name silently fails in a packaged jar — that is why the selection
 * buttons never appeared in-game. This screen draws only with obfuscation-stable API.
 *
 * <p>Opened as a NeoForge <em>GUI layer</em> ({@code Minecraft#pushGuiLayer}); closing
 * pops back to whatever was underneath without touching the server-side menu.
 *
 * <p>Row geometry lives in the pure, unit-tested {@link #computeLayout}.
 */
public class TerminalSelectorScreen extends Screen {

    static final int ROW_HEIGHT = 20;
    static final int ROW_GAP = 4;
    static final int PANEL_PADDING = 12;
    static final int MIN_PANEL_WIDTH = 200;
    static final int MAX_PANEL_WIDTH = 280;
    /** Terminal rows beyond this count scroll instead of overflowing the screen. */
    static final int MAX_VISIBLE_ROWS = 10;
    /** Space reserved on the right of each row for the Name / Save button. */
    static final int NAME_BUTTON_WIDTH = 40;
    static final int NAME_BUTTON_GAP = 4;
    /** Below this main-button width we hide the Name button so the row stays usable. */
    static final int MIN_MAIN_BUTTON_WIDTH = 80;

    private final List<TerminalInfo> terminals = new ArrayList<>();
    private int scrollOffset;
    /** {@link TerminalInfo#nameKey()} of the row currently in rename edit mode, or null. */
    private String editingKey;
    /** Live edit box while editing; null when not editing. Read on Save click. */
    private EditBox editBox;

    public TerminalSelectorScreen(List<TerminalInfo> terminals) {
        super(Component.translatable("screen.ultra_wireless_terminal_2.selector"));
        if (terminals != null) {
            this.terminals.addAll(terminals);
        }
    }

    public List<TerminalInfo> terminals() {
        return List.copyOf(terminals);
    }

    /**
     * Replace the shown list (fresh server push while this screen is open). Keeps the
     * in-progress edit if its row is still there, otherwise drops edit mode.
     */
    public void setTerminals(List<TerminalInfo> newTerminals) {
        terminals.clear();
        if (newTerminals != null) {
            terminals.addAll(newTerminals);
        }
        if (editingKey != null) {
            boolean stillPresent = terminals.stream().anyMatch(t -> editingKey.equals(t.nameKey()));
            if (!stillPresent) {
                editingKey = null;
                editBox = null;
            }
        }
        clampScroll();
        rebuildWidgets();
    }

    /** A button rect for one terminal row plus its Name / Save button. */
    public record RowLayout(TerminalInfo info, int x, int y, int width, int height,
            int nameX, int nameWidth) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
        /** True when this narrow layout hides the Name button entirely. */
        public boolean hasNameButton() {
            return nameWidth > 0;
        }
    }

    public record Layout(int panelX, int panelY, int panelWidth, int panelHeight,
            List<RowLayout> rows, int closeX, int closeY, int closeWidth, int closeHeight) {}

    /**
     * How many rows to show: at most {@link #MAX_VISIBLE_ROWS}, fewer on short screens,
     * but always at least one so a single terminal is never hidden.
     */
    public static int visibleCount(int total, int screenHeight) {
        int chrome = PANEL_PADDING * 2 + 9 + 6 + 6 + 20;
        int fit = Math.max(1, (screenHeight - 12 - chrome) / (ROW_HEIGHT + ROW_GAP));
        return Math.min(Math.min(total, MAX_VISIBLE_ROWS), fit);
    }

    public static Layout computeLayout(List<TerminalInfo> terminals, int screenWidth, int screenHeight,
            int scrollOffset) {
        int total = terminals == null ? 0 : terminals.size();
        int visible = total == 0 ? 0 : visibleCount(total, screenHeight);
        // never wider than the screen (tiny GUI scales)
        int panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, Math.min(MAX_PANEL_WIDTH, screenWidth - 40)),
                Math.max(60, screenWidth - 8));
        int panelHeight = PANEL_PADDING + 9 + 6
                + visible * (ROW_HEIGHT + ROW_GAP)
                + 6 + 20 + PANEL_PADDING;
        int panelX = (screenWidth - panelWidth) / 2;
        int panelY = Math.max(6, (screenHeight - panelHeight) / 2);
        int rowWidth = panelWidth - 2 * PANEL_PADDING;
        // shrink main button to leave room for the Name button, unless that leaves it tiny
        boolean showName = rowWidth - NAME_BUTTON_GAP - NAME_BUTTON_WIDTH >= MIN_MAIN_BUTTON_WIDTH;
        int mainWidth = showName ? rowWidth - NAME_BUTTON_GAP - NAME_BUTTON_WIDTH : rowWidth;
        int nameWidth = showName ? NAME_BUTTON_WIDTH : 0;
        int nameX = panelX + PANEL_PADDING + mainWidth + NAME_BUTTON_GAP;
        List<RowLayout> rows = new ArrayList<>();
        int y = panelY + PANEL_PADDING + 9 + 6;
        if (terminals != null) {
            for (int i = scrollOffset; i < scrollOffset + visible && i < total; i++) {
                rows.add(new RowLayout(terminals.get(i), panelX + PANEL_PADDING, y,
                        mainWidth, ROW_HEIGHT, nameX, nameWidth));
                y += ROW_HEIGHT + ROW_GAP;
            }
        }
        int closeWidth = Math.min(100, rowWidth);
        return new Layout(panelX, panelY, panelWidth, panelHeight, rows,
                panelX + (panelWidth - closeWidth) / 2,
                panelY + panelHeight - 20 - PANEL_PADDING,
                closeWidth, 20);
    }

    @Override
    protected void init() {
        clampScroll();
        Layout layout = computeLayout(terminals, width, height, scrollOffset);
        editBox = null;
        for (RowLayout row : layout.rows()) {
            TerminalInfo info = row.info();
            boolean editing = info.nameKey().equals(editingKey);
            if (editing) {
                EditBox box = new EditBox(font, row.x(), row.y(), row.width(), row.height(),
                        Component.translatable("screen.ultra_wireless_terminal_2.rename_hint"));
                box.setMaxLength(64);
                box.setValue(info.name() == null ? "" : info.name());
                box.setHint(Component.literal(TerminalOverlay.shortLabel(info)));
                addRenderableWidget(box);
                editBox = box;
                if (row.hasNameButton()) {
                    addRenderableWidget(Button.builder(
                                    Component.translatable("screen.ultra_wireless_terminal_2.save"),
                                    b -> saveRename(info))
                            .bounds(row.nameX(), row.y(), row.nameWidth(), row.height())
                            .build());
                }
            } else {
                Button button = Button.builder(rowLabel(info), b -> activate(info))
                        .bounds(row.x(), row.y(), row.width(), row.height())
                        .build();
                button.active = info.inRange() && !info.active();
                button.setTooltip(Tooltip.create(rowTooltip(info)));
                addRenderableWidget(button);
                if (row.hasNameButton()) {
                    Button nameButton = Button.builder(
                                    Component.translatable("screen.ultra_wireless_terminal_2.rename"),
                                    b -> beginRename(info))
                            .bounds(row.nameX(), row.y(), row.nameWidth(), row.height())
                            .build();
                    addRenderableWidget(nameButton);
                }
            }
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(layout.closeX(), layout.closeY(), layout.closeWidth(), layout.closeHeight())
                .build());
        if (editBox != null) {
            setInitialFocus(editBox);
            editBox.setFocused(true);
            editBox.setCanLoseFocus(false);
        }
    }

    private void beginRename(TerminalInfo info) {
        editingKey = info.nameKey();
        rebuildWidgets();
    }

    private void saveRename(TerminalInfo info) {
        String value = editBox == null ? "" : editBox.getValue();
        // fall back to the pre-edit value if a caller saved with an empty box (Escape path)
        UltraWirelessTerminalNetworkHandler.sendRename(
                info.dimName(), info.x(), info.y(), info.z(), value);
        editingKey = null;
        editBox = null;
        // server pushes a fresh list which re-renders the row with the new label
    }

    private void activate(TerminalInfo info) {
        // arms the lost-switch watchdog, then asks the server to set it active + open
        // that terminal's GUI (replacing this screen) and push a fresh list
        TerminalOverlay.beginActivate(info);
    }

    /** Scroll the row window when it overflows (arrow keys / mouse wheel). */
    private void cycleVisible(int direction) {
        int total = terminals.size();
        if (total == 0) {
            return;
        }
        int visible = visibleCount(total, height);
        if (total > visible) {
            scrollOffset = clamp(scrollOffset + direction, 0, total - visible);
            rebuildWidgets();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private void clampScroll() {
        int visible = visibleCount(terminals.size(), height);
        scrollOffset = clamp(scrollOffset, 0, Math.max(0, terminals.size() - visible));
    }

    private Component rowLabel(TerminalInfo info) {
        // TerminalInfo.label() already prefers the custom name; shortLabel for unnamed rows
        String label = (info.name() != null && !info.name().isBlank())
                ? info.name()
                : TerminalOverlay.shortLabel(info);
        if (info.active()) {
            return Component.translatable("screen.ultra_wireless_terminal_2.row_active", label);
        }
        if (!info.inRange()) {
            return Component.translatable("screen.ultra_wireless_terminal_2.row_unreachable", label);
        }
        return Component.literal(label);
    }

    private Component rowTooltip(TerminalInfo info) {
        MutableComponent name = Component.literal(info.label());
        Component state;
        if (info.active()) {
            state = Component.translatable("overlay.ultra_wireless_terminal_2.active");
        } else if (info.inRange()) {
            state = Component.translatable("overlay.ultra_wireless_terminal_2.click_to_switch");
        } else {
            state = Component.translatable("overlay.ultra_wireless_terminal_2.out_of_range");
        }
        return name.append(Component.literal(" - ")).append(state);
    }

    /**
     * No-op: the vanilla background path runs {@code GameRenderer#processBlurEffect}
     * (the menu blur) over the whole framebuffer, which smears the terminal / world behind
     * this screen into mush — the exact "text behind a menu" blur. {@link Screen#render}
     * calls this, so overriding it (rather than just not calling it) is what actually
     * stops the blur. We supply our own flat scrim in {@link #render} instead.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim whatever is underneath with a plain translucent scrim (crisp, no blur).
        graphics.fill(0, 0, this.width, this.height, 0x90000000);
        Layout layout = computeLayout(terminals, width, height, scrollOffset);
        int x0 = layout.panelX() - 6;
        int y0 = layout.panelY() - 6;
        int x1 = layout.panelX() + layout.panelWidth() + 6;
        int y1 = layout.panelY() + layout.panelHeight() + 6;
        graphics.fill(x0, y0, x1, y1, 0xF014141C);
        graphics.renderOutline(x0, y0, x1 - x0, y1 - y0, 0xFF5A5A78);
        graphics.drawCenteredString(font, title, layout.panelX() + layout.panelWidth() / 2,
                layout.panelY() + PANEL_PADDING - 3, 0xFFE8E8FF);
        super.render(graphics, mouseX, mouseY, partialTick); // widgets + their tooltips
        if (terminals.size() > layout.rows().size()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.ultra_wireless_terminal_2.scrolling",
                            scrollOffset + 1, scrollOffset + layout.rows().size(), terminals.size()),
                    width / 2, y1 + 4, 0xFFB0B0C0);
        } else {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.ultra_wireless_terminal_2.hint"),
                    width / 2, y1 + 4, 0xFFB0B0C0);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        cycleVisible(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // While renaming, only ESC (cancel) and Enter (save) are ours; everything else
        // (typing, arrows for the caret, backspace) must reach the focused EditBox.
        if (editingKey != null) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                editingKey = null;
                editBox = null;
                rebuildWidgets();
                return true;
            }
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                TerminalInfo editing = findEditing();
                if (editing != null) {
                    saveRename(editing);
                    return true;
                }
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == InputConstants.KEY_DOWN || keyCode == InputConstants.KEY_RIGHT) {
            cycleVisible(1);
            return true;
        }
        if (keyCode == InputConstants.KEY_UP || keyCode == InputConstants.KEY_LEFT) {
            cycleVisible(-1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private TerminalInfo findEditing() {
        for (TerminalInfo t : terminals) {
            if (t.nameKey().equals(editingKey)) {
                return t;
            }
        }
        return null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

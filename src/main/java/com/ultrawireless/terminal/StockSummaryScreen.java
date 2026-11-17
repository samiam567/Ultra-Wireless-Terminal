package com.ultrawireless.terminal;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The Ultra Wireless Terminal's own stock-ticker view: the live contents of the linked
 * Create logistics network. Because Create's network summary is a distance-free server
 * cache (see {@link StockTickerProxy}), this is opened from <em>anywhere</em> and no
 * stock ticker block has to be loaded or nearby.
 *
 * <p>Opened from the world or as a NeoForge GUI layer over another screen: Escape or Done
 * pops back. Line windowing is the pure, unit-tested {@link #computeVisible}.
 */
public class StockSummaryScreen extends Screen {

    static final int LINE_HEIGHT = 11;
    static final int MAX_VISIBLE_LINES = 14;

    private final List<String> lines;
    private int scroll;

    public StockSummaryScreen(List<String> lines) {
        super(Component.translatable("screen.ultra_wireless_terminal_2.stock_summary"));
        this.lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /** First visible line index and count, given a scroll offset. Pure. */
    public record Window(int first, int count) {}

    public static Window computeVisible(List<String> lines, int scroll) {
        int total = lines == null ? 0 : lines.size();
        int start = Math.max(0, Math.min(scroll, Math.max(0, total - MAX_VISIBLE_LINES)));
        int count = Math.min(MAX_VISIBLE_LINES, Math.max(0, total - start));
        return new Window(start, count);
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(120, this.width - 40);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds((this.width - buttonWidth) / 2, this.height - 34, buttonWidth, 20)
                .build());
    }

    /**
     * No-op: the vanilla background path runs {@code GameRenderer#processBlurEffect} (the
     * menu blur) over the whole framebuffer, smearing the world / terminal behind this
     * panel. {@link Screen#render} calls it, so overriding it is what actually stops the
     * blur; {@link #render} draws its own flat scrim.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Flat translucent scrim (crisp, no menu blur behind the panel).
        graphics.fill(0, 0, this.width, this.height, 0x90000000);
        Window window = computeVisible(lines, scroll);
        int panelWidth = Math.min(340, this.width - 20);
        int panelHeight = Math.min(MAX_VISIBLE_LINES * LINE_HEIGHT + 60, this.height - 60);
        int x0 = (this.width - panelWidth) / 2;
        int y0 = Math.max(8, (this.height - panelHeight) / 2);
        graphics.fill(x0, y0, x0 + panelWidth, y0 + panelHeight, 0xF014141C);
        graphics.renderOutline(x0, y0, panelWidth, panelHeight, 0xFF5A5A78);
        graphics.drawCenteredString(font, title, this.width / 2, y0 + 10, 0xFFE8E8FF);
        int textX = x0 + 12;
        int y = y0 + 30;
        for (int i = window.first(); i < window.first() + window.count(); i++) {
            graphics.drawString(font, wrap(lines.get(i), panelWidth - 24), textX, y, 0xFFD8D8D8);
            y += LINE_HEIGHT;
        }
        if (lines.size() > MAX_VISIBLE_LINES) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.ultra_wireless_terminal_2.scrolling",
                            window.first() + 1, window.first() + window.count(), lines.size()),
                    this.width / 2, y0 + panelHeight - 26, 0xFFB0B0C0);
        }
        super.render(graphics, mouseX, mouseY, partialTick); // Done button
    }

    private net.minecraft.util.FormattedCharSequence wrap(String line, int maxWidth) {
        return font.split(Component.literal(line), Math.max(40, maxWidth)).get(0);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (scrollY > 0 ? 1 : -1));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_DOWN) {
            scroll = Math.min(Math.max(0, lines.size() - MAX_VISIBLE_LINES), scroll + 1);
            return true;
        }
        if (keyCode == InputConstants.KEY_UP) {
            scroll = Math.max(0, scroll - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

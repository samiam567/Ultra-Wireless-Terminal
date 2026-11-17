package com.ultrawireless.terminal;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ultrawireless.terminal.StockSummaryScreen.Window;
import com.ultrawireless.terminal.TerminalSelectorScreen.Layout;
import com.ultrawireless.terminal.TerminalSelectorScreen.RowLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure geometry tests for {@link TerminalSelectorScreen#computeLayout} and
 * {@link StockSummaryScreen#computeVisible} — the GUIs must stay on-screen and clip
 * gracefully at every resolution.
 */
public class SelectorScreenLayoutTest {

    private static TerminalInfo info(int x, boolean inRange, boolean active) {
        return new TerminalInfo("minecraft:overworld", x, 64, 0, inRange, active);
    }

    private static List<TerminalInfo> many(int n) {
        List<TerminalInfo> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(info(i, true, i == 0));
        }
        return list;
    }

    @Test
    void panelCentredAndRowsInside() {
        Layout layout = TerminalSelectorScreen.computeLayout(many(3), 427, 240, 0);
        // horizontally centred
        assertTrue(layout.panelX() >= 0);
        assertTrue(layout.panelX() + layout.panelWidth() <= 427);
        assertEquals((427 - layout.panelWidth()) / 2, layout.panelX());
        assertEquals(3, layout.rows().size());
        for (RowLayout row : layout.rows()) {
            assertTrue(row.y() >= layout.panelY());
            assertTrue(row.y() + row.height() <= layout.panelY() + layout.panelHeight());
            assertTrue(row.x() >= layout.panelX());
            assertTrue(row.x() + row.width() <= layout.panelX() + layout.panelWidth());
        }
        // close button inside panel, below rows
        assertTrue(layout.closeY() > layout.rows().get(2).y());
        assertTrue(layout.closeY() + layout.closeHeight() <= layout.panelY() + layout.panelHeight());
    }

    @Test
    void longListClipsToMaxVisibleRows() {
        Layout layout = TerminalSelectorScreen.computeLayout(many(30), 854, 480, 0);
        assertEquals(TerminalSelectorScreen.MAX_VISIBLE_ROWS, layout.rows().size());
        assertEquals(many(30).get(0).label(), layout.rows().get(0).info().label());
        assertTrue(layout.panelHeight() <= 480,
                "panel of " + layout.panelHeight() + " must fit 480px screen");
        assertTrue(layout.panelY() >= 6);
    }

    @Test
    void shortScreensShowFewerRowsButAlwaysOne() {
        // 200px tall: chrome (65) + rows must fit -> fewer than MAX rows
        int visible = TerminalSelectorScreen.visibleCount(30, 200);
        assertTrue(visible >= 1 && visible < TerminalSelectorScreen.MAX_VISIBLE_ROWS,
                "expected reduced row count, got " + visible);
        Layout layout = TerminalSelectorScreen.computeLayout(many(30), 854, 200, 0);
        assertEquals(visible, layout.rows().size());
        assertTrue(layout.panelHeight() + 12 <= 200,
                "panel of " + layout.panelHeight() + " must fit 200px screen");
        // near-minimum screen: exactly one row, panel stays on-screen from y=6
        assertEquals(1, TerminalSelectorScreen.visibleCount(30, 90));
        Layout tiny = TerminalSelectorScreen.computeLayout(many(30), 854, 90, 0);
        assertEquals(1, tiny.rows().size());
        assertTrue(tiny.panelY() >= 6);
        // panel bottom only clips if the screen is below the hard minimum (~89px chrome)
        assertTrue(tiny.panelY() + tiny.panelHeight() <= Math.max(90, tiny.panelHeight() + 12));
    }

    @Test
    void scrollingShiftsVisibleWindow() {
        List<TerminalInfo> terminals = many(30);
        Layout scrolled = TerminalSelectorScreen.computeLayout(terminals, 854, 480, 5);
        assertEquals(terminals.get(5), scrolled.rows().get(0).info());
        assertEquals(TerminalSelectorScreen.MAX_VISIBLE_ROWS, scrolled.rows().size());
    }

    @Test
    void emptyListKeepsPanelWithCloseButton() {
        Layout layout = TerminalSelectorScreen.computeLayout(List.of(), 427, 240, 0);
        assertEquals(0, layout.rows().size());
        assertTrue(layout.closeY() > layout.panelY());
    }

    @Test
    void tinyScreenShrinksPanel() {
        Layout layout = TerminalSelectorScreen.computeLayout(many(2), 120, 80, 0);
        assertTrue(layout.panelX() >= 0);
        assertTrue(layout.panelWidth() <= 120, "panel must not exceed a 120px screen");
        assertTrue(layout.panelHeight() + 12 <= 80 || layout.rows().size() == 1);
    }

    // ---- rename button column ----

    @Test
    void nameButtonFitsBesideMainButtonOnNormalScreens() {
        Layout layout = TerminalSelectorScreen.computeLayout(many(3), 854, 480, 0);
        for (RowLayout row : layout.rows()) {
            assertTrue(row.hasNameButton(), "roomy layout should show the Name button");
            assertTrue(row.nameX() >= row.x() + row.width(), "name button is right of the main button");
            assertTrue(row.nameX() + row.nameWidth() <= layout.panelX() + layout.panelWidth(),
                    "name button stays inside the panel");
        }
    }

    @Test
    void nameButtonHiddenWhenRowTooNarrow() {
        // 120px wide screen: main button would shrink below MIN_MAIN_BUTTON_WIDTH, so the
        // Name button disappears entirely and the main button takes the full row
        Layout layout = TerminalSelectorScreen.computeLayout(many(2), 120, 240, 0);
        for (RowLayout row : layout.rows()) {
            assertFalse(row.hasNameButton(), "no room for a Name button on a tiny screen");
            assertEquals(0, row.nameWidth());
        }
    }

    // ---- stock summary windowing ----

    @Test
    void summaryNoScrollWhenShort() {
        Window w = StockSummaryScreen.computeVisible(List.of("a", "b", "c"), 0);
        assertEquals(0, w.first());
        assertEquals(3, w.count());
    }

    @Test
    void summaryClampsScroll() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            lines.add("line " + i);
        }
        Window w = StockSummaryScreen.computeVisible(lines, 1000);
        assertEquals(100 - StockSummaryScreen.MAX_VISIBLE_LINES, w.first());
        assertEquals(StockSummaryScreen.MAX_VISIBLE_LINES, w.count());

        Window negative = StockSummaryScreen.computeVisible(lines, -5);
        assertEquals(0, negative.first());
    }

    @Test
    void summaryScrollsFromMiddle() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            lines.add("line " + i);
        }
        Window w = StockSummaryScreen.computeVisible(lines, 10);
        assertEquals(10, w.first());
        assertEquals(StockSummaryScreen.MAX_VISIBLE_LINES, w.count());
    }

    @Test
    void summaryEmptySafe() {
        Window w = StockSummaryScreen.computeVisible(List.of(), 0);
        assertEquals(0, w.first());
        assertEquals(0, w.count());
    }
}

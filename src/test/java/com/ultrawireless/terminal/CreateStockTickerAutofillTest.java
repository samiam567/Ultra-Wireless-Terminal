package com.ultrawireless.terminal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ultrawireless.terminal.CreateStockTickerAutofill.RawClipboardEntry;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateStockTickerAutofillTest {

    private static RawClipboardEntry entry(boolean checked, String text) {
        return new RawClipboardEntry(checked, text);
    }

    @Test
    void nullEntriesYieldEmptyList() {
        assertEquals(List.of(), CreateStockTickerAutofill.extractAddressesFromEntries(null));
    }

    @Test
    void emptyEntriesYieldEmptyList() {
        assertEquals(List.of(), CreateStockTickerAutofill.extractAddressesFromEntries(List.of()));
    }

    @Test
    void extractsHashAddressesAndStripsHash() {
        List<RawClipboardEntry> entries = List.of(
                entry(false, "#Storage"),
                entry(false, "#Main Bay")
        );
        assertEquals(List.of("Storage", "Main Bay"), CreateStockTickerAutofill.extractAddressesFromEntries(entries));
    }

    @Test
    void skipsCheckedEntries() {
        List<RawClipboardEntry> entries = List.of(
                entry(true, "#Done Task"),
                entry(false, "#Active Address"),
                entry(true, "#Another Completed")
        );
        assertEquals(List.of("Active Address"), CreateStockTickerAutofill.extractAddressesFromEntries(entries));
    }

    @Test
    void skipsBlankOrHashOnlyEntries() {
        List<RawClipboardEntry> entries = List.of(
                entry(false, "#"),
                entry(false, "#   "),
                entry(false, "   "),
                entry(false, "#Valid")
        );
        assertEquals(List.of("Valid"), CreateStockTickerAutofill.extractAddressesFromEntries(entries));
    }

    @Test
    void deduplicatesAddressesPreservingOrder() {
        List<RawClipboardEntry> entries = List.of(
                entry(false, "#Base"),
                entry(false, "#Depot"),
                entry(false, "#Base"),
                entry(false, "#Factory")
        );
        assertEquals(List.of("Base", "Depot", "Factory"), CreateStockTickerAutofill.extractAddressesFromEntries(entries));
    }

    @Test
    void fallbackToNonHashEntriesWhenNoHashEntriesExist() {
        List<RawClipboardEntry> entries = List.of(
                entry(false, "Central Warehouse"),
                entry(false, "Outpost 4")
        );
        assertEquals(List.of("Central Warehouse", "Outpost 4"), CreateStockTickerAutofill.extractAddressesFromEntries(entries));
    }

    @Test
    void hashEntriesTakePrecedenceOverNonHashEntries() {
        List<RawClipboardEntry> entries = List.of(
                entry(false, "Random Note"),
                entry(false, "#Actual Address"),
                entry(false, "Shopping List Item")
        );
        assertEquals(List.of("Actual Address"), CreateStockTickerAutofill.extractAddressesFromEntries(entries));
    }

    @Test
    void autofillClassHasPublicConstructorAndInstanceSubscribeEvent() throws Exception {
        Class<?> clazz = Class.forName("com.ultrawireless.terminal.CreateStockTickerAutofill");

        // Must have a public no-arg constructor so reflection instantiation succeeds
        Constructor<?> ctor = clazz.getDeclaredConstructor();
        assertTrue(Modifier.isPublic(ctor.getModifiers()),
                "CreateStockTickerAutofill must have a public constructor");

        // onScreenInit must be an instance method annotated with @SubscribeEvent
        Method method = clazz.getDeclaredMethod("onScreenInit", ScreenEvent.Init.Post.class);
        assertNotNull(method);
        assertTrue(method.isAnnotationPresent(SubscribeEvent.class),
                "onScreenInit must have @SubscribeEvent annotation");
        assertFalse(Modifier.isStatic(method.getModifiers()),
                "onScreenInit must be an INSTANCE method, not static, so EventBus.register(instance) binds it");
    }
}

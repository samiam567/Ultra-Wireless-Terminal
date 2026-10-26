package com.ultrawireless.terminal;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventBusRegistrationTest {

	@Test
	void clientTickHandlerIsBoundToNeoForgeBus() throws Exception {
		// ClientTickEvent.Post is a NeoForge bus event, not a mod-bus event, so the
		// handler must carry @SubscribeEvent and be registered on NeoForge.EVENT_BUS.
		Method tick = UltraWirelessTerminalClient.class.getDeclaredMethod(
				"onClientTick", ClientTickEvent.Post.class);
		assertTrue(tick.isAnnotationPresent(SubscribeEvent.class),
				"onClientTick must be a @SubscribeEvent handler bound to the NeoForge bus");
	}

	@Test
	void clientTickHandlerAcceptsOnlyNeoForgePostEvent() throws Exception {
		Method tick = UltraWirelessTerminalClient.class.getDeclaredMethod(
				"onClientTick", ClientTickEvent.Post.class);
		// The handler parameter is a NeoForge client tick event, not a mod bus event.
		assertEquals(ClientTickEvent.Post.class, tick.getParameterTypes()[0]);
	}
}

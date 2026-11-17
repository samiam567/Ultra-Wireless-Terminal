package com.ultrawireless.terminal;

import org.junit.jupiter.api.Test;

import com.ultrawireless.terminal.TerminalOverlay.ActivationRetry;
import com.ultrawireless.terminal.TerminalOverlay.ActivationRetry.Outcome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure state-machine tests for {@link ActivationRetry}: the watchdog that re-sends a
 * terminal switch when the replacement GUI fails to open.
 */
public class ActivationRetryTest {

    private static TerminalInfo info(int x) {
        return new TerminalInfo("minecraft:overworld", x, 64, 0, true, false);
    }

    @Test
    void idleStateMachineGivesUp() {
        ActivationRetry r = new ActivationRetry();
        assertEquals(Outcome.GIVE_UP, r.onTick(false));
        assertFalse(r.pending());
    }

    @Test
    void landedOpenCancelsWatchdog() {
        ActivationRetry r = new ActivationRetry();
        TerminalInfo target = info(10);
        r.begin(target);
        assertTrue(r.pending());
        assertSame(target, r.pendingInfo());

        // GUI opened well before the deadline
        for (int i = 0; i < 5; i++) {
            assertEquals(Outcome.WAIT, r.onTick(false));
        }
        assertEquals(Outcome.WAIT, r.onTick(true));
        assertFalse(r.pending());
    }

    @Test
    void lostOpenRetriesOnceThenGivesUp() {
        ActivationRetry r = new ActivationRetry();
        r.begin(info(20));

        // never a container screen: run past the deadline -> exactly one resend
        Outcome outcome = Outcome.WAIT;
        for (int i = 0; i < ActivationRetry.DEADLINE_TICKS && outcome == Outcome.WAIT; i++) {
            outcome = r.onTick(false);
        }
        assertEquals(Outcome.RETRY, outcome);
        assertTrue(r.pending(), "still watching after the resend");

        // second deadline expires -> give up, clear state
        outcome = Outcome.WAIT;
        for (int i = 0; i < ActivationRetry.DEADLINE_TICKS + 1 && outcome == Outcome.WAIT; i++) {
            outcome = r.onTick(false);
        }
        assertEquals(Outcome.GIVE_UP, outcome);
        assertFalse(r.pending());
        assertNull(r.pendingInfo());
    }

    @Test
    void cancelStopsEverything() {
        ActivationRetry r = new ActivationRetry();
        r.begin(info(30));
        r.cancel();
        assertFalse(r.pending());
        assertEquals(Outcome.GIVE_UP, r.onTick(false));
    }

    @Test
    void briefInventoryFrameDoesNotTriggerResend() {
        // close -> open swap can leave one tick without a container screen; a single
        // frame gap must never resend
        ActivationRetry r = new ActivationRetry();
        r.begin(info(40));
        assertEquals(Outcome.WAIT, r.onTick(false));
        assertEquals(Outcome.WAIT, r.onTick(true));
        assertFalse(r.pending());
    }
}

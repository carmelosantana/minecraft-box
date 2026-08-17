/*
 * The Box - a rare nocturnal stalker that moves only while unobserved and feeds on the
 * experience of whoever watches it.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.box.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Truth table for {@link BoxLifecycleListener#vulnerable(double)} — the sole unit-testable seam of
 * the lifecycle listener and the pure heart of acceptance check 7 (closed: damage denied; open:
 * damage applies).
 *
 * <p>The predicate mirrors the shulker's {@code getPeek()} value: a sealed shell ({@code peek <= 0})
 * is the closed-armor window and is invulnerable; any positive peek means the shell is open and
 * feeding, which is the only window in which damage lands. The live wiring — reading {@code getPeek()}
 * off a running entity and cancelling {@link org.bukkit.event.entity.EntityDamageEvent} — is a
 * gate-7a obligation and is not exercised here.
 */
class DamageGateTest {

    @Test
    void sealedShellIsInvulnerable() {
        assertFalse(BoxLifecycleListener.vulnerable(0.0),
                "a fully sealed shell (peek 0) is the closed-armor window and takes no damage");
    }

    @Test
    void negativePeekIsInvulnerable() {
        // getPeek() is never negative in practice, but the predicate is defined as strictly > 0 so a
        // defensive negative still reads as sealed rather than accidentally vulnerable.
        assertFalse(BoxLifecycleListener.vulnerable(-0.25),
                "a negative peek is treated as sealed");
    }

    @Test
    void crackedOpenIsVulnerable() {
        assertTrue(BoxLifecycleListener.vulnerable(0.01),
                "any positive peek means the shell has opened and damage applies");
    }

    @Test
    void halfOpenIsVulnerable() {
        assertTrue(BoxLifecycleListener.vulnerable(0.5), "a half-open shell is vulnerable");
    }

    @Test
    void fullyOpenIsVulnerable() {
        assertTrue(BoxLifecycleListener.vulnerable(1.0),
                "a fully open, feeding shell is vulnerable — this is the intended kill window");
    }
}

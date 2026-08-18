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
 * Truth table for {@link BoxLifecycleListener#shouldRedeem(boolean, boolean, boolean)} — the pure
 * decision gating artifact redemption on a {@code PlayerInteractEvent} (acceptance checks 12, 18,
 * 22). Redemption fires only for a main-hand right-click holding a Box artifact.
 *
 * <p>The live {@code consume} path this predicate gates — reading the item PDC, granting XP via
 * {@code giveExp}, and dispatching the curse console command — needs a running server and is a
 * gate-7a obligation documented on {@link BoxLifecycleListener}; it is deliberately not exercised
 * here.
 */
class RedemptionGateTest {

    @Test
    void redeemsOnlyForMainHandRightClickWithArtifact() {
        assertTrue(BoxLifecycleListener.shouldRedeem(true, true, true),
                "a main-hand right-click holding an artifact must redeem");
    }

    @Test
    void offHandNeverRedeems() {
        // Guards against the paired off-hand fire of a single click double-redeeming.
        assertFalse(BoxLifecycleListener.shouldRedeem(false, true, true),
                "the off-hand fire of the same click must not redeem");
    }

    @Test
    void nonRightClickNeverRedeems() {
        assertFalse(BoxLifecycleListener.shouldRedeem(true, false, true),
                "a left-click / physical action must not redeem");
    }

    @Test
    void nonArtifactNeverRedeems() {
        assertFalse(BoxLifecycleListener.shouldRedeem(true, true, false),
                "a right-click with an ordinary item must not redeem");
    }

    @Test
    void allFalseNeverRedeems() {
        assertFalse(BoxLifecycleListener.shouldRedeem(false, false, false));
    }
}

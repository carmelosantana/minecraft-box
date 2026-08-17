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
package org.xpfarm.box.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the single Bukkit-free decision seam of {@link ArtifactService}: the
 * {@code shouldStartCurse} guard (acceptance check 18), asserted over its full two-flag truth
 * table.
 *
 * <p>The live effects of {@code build}, {@code isArtifact}, and {@code consume} (ItemStack/PDC
 * construction via {@code Bukkit.getItemFactory()}, {@code Player#giveExp}, and console-command
 * dispatch) cannot be faithfully exercised without a running server; they are gate-7a obligations
 * documented on {@link ArtifactService} and verified against a live client (acceptance checks 12,
 * 18). They are deliberately not unit-tested here — {@code Bukkit.getItemFactory()} NPEs headlessly.
 */
class ArtifactServiceTest {

    /** Only both flags together start a curse; every other combination stays silent. */
    @Test void shouldStartCurseOnlyWhenBothFlagsTrue() {
        assertFalse(ArtifactService.shouldStartCurse(false, false));
        assertFalse(ArtifactService.shouldStartCurse(true, false));
        assertFalse(ArtifactService.shouldStartCurse(false, true));
        assertTrue(ArtifactService.shouldStartCurse(true, true));
    }

    /** With integration configured but the Curse plugin absent, no curse is dispatched (check 18). */
    @Test void curseAbsentNeverStarts() {
        assertFalse(ArtifactService.shouldStartCurse(true, false));
    }

    /** With the Curse plugin present but integration disabled in config, no curse is dispatched. */
    @Test void integrationDisabledNeverStarts() {
        assertFalse(ArtifactService.shouldStartCurse(false, true));
    }
}

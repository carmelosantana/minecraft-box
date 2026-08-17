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

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the extracted {@link GazeService#shouldConsider(GameMode, boolean, boolean)}
 * eligibility predicate. This is the only unit-testable seam of the adapter; the live gaze paths
 * (eye vectors, cone, distance, raytrace occlusion) are gate-7a obligations documented on
 * {@link GazeService} and verified against a running server, not mocked here.
 */
class GazeServiceTest {

    // --- Survival: always eligible regardless of the ignore flags. ---
    @Test void survivalAlwaysConsidered() {
        assertTrue(GazeService.shouldConsider(GameMode.SURVIVAL, true, true));
        assertTrue(GazeService.shouldConsider(GameMode.SURVIVAL, false, false));
    }

    // --- Adventure: treated like survival, always eligible. ---
    @Test void adventureAlwaysConsidered() {
        assertTrue(GazeService.shouldConsider(GameMode.ADVENTURE, true, true));
        assertTrue(GazeService.shouldConsider(GameMode.ADVENTURE, false, false));
    }

    // --- Creative: excluded only when ignoreCreative is on. ---
    @Test void creativeExcludedWhenIgnoreCreative() {
        assertFalse(GazeService.shouldConsider(GameMode.CREATIVE, true, true));
        assertFalse(GazeService.shouldConsider(GameMode.CREATIVE, true, false));
    }

    @Test void creativeConsideredWhenNotIgnored() {
        assertTrue(GazeService.shouldConsider(GameMode.CREATIVE, false, true));
        assertTrue(GazeService.shouldConsider(GameMode.CREATIVE, false, false));
    }

    // --- Spectator: excluded only when ignoreSpectator is on. ---
    @Test void spectatorExcludedWhenIgnoreSpectator() {
        assertFalse(GazeService.shouldConsider(GameMode.SPECTATOR, true, true));
        assertFalse(GazeService.shouldConsider(GameMode.SPECTATOR, false, true));
    }

    @Test void spectatorConsideredWhenNotIgnored() {
        assertTrue(GazeService.shouldConsider(GameMode.SPECTATOR, true, false));
        assertTrue(GazeService.shouldConsider(GameMode.SPECTATOR, false, false));
    }

    // --- The two flags are independent: creative ignored but spectator not, and vice versa. ---
    @Test void flagsAreIndependent() {
        // ignoreCreative only: creative out, spectator in.
        assertFalse(GazeService.shouldConsider(GameMode.CREATIVE, true, false));
        assertTrue(GazeService.shouldConsider(GameMode.SPECTATOR, true, false));
        // ignoreSpectator only: spectator out, creative in.
        assertFalse(GazeService.shouldConsider(GameMode.SPECTATOR, false, true));
        assertTrue(GazeService.shouldConsider(GameMode.CREATIVE, false, true));
    }
}

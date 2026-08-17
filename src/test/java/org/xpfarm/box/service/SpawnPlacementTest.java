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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure seams of {@link SpawnService}: the night-window predicate
 * ({@link SpawnService#isNight}) and the combined cap predicate
 * ({@link SpawnService#withinCaps}).
 *
 * <p>The live surface — {@code rollFor} and {@code spawnAt} (the world spawn, the distance-band
 * placement, the sky-access probe, and the view-cone exclusion) — depends on a running server's
 * {@code World}, {@code Player}, and {@code Shulker} and is a gate-7a obligation (acceptance
 * check 1 and the natural-spawn conditions). It is documented in the service Javadoc, not mocked
 * here: a faithful headless {@code World}/{@code Player} cannot be constructed without a registry.
 */
class SpawnPlacementTest {

    @Nested
    class IsNight {

        // Default overworld night window is 13000..23000 ticks (inclusive both ends).

        @Test
        void startBoundaryIsNight() {
            assertTrue(SpawnService.isNight(13000, 13000, 23000));
        }

        @Test
        void endBoundaryIsNight() {
            assertTrue(SpawnService.isNight(23000, 13000, 23000));
        }

        @Test
        void midWindowIsNight() {
            assertTrue(SpawnService.isNight(18000, 13000, 23000));
        }

        @Test
        void justBeforeStartIsNotNight() {
            assertFalse(SpawnService.isNight(12999, 13000, 23000));
        }

        @Test
        void justAfterEndIsNotNight() {
            assertFalse(SpawnService.isNight(23001, 13000, 23000));
        }

        @Test
        void middayIsNotNight() {
            assertFalse(SpawnService.isNight(0, 13000, 23000));
            assertFalse(SpawnService.isNight(6000, 13000, 23000));
        }

        @Test
        void aWindowThatDoesNotContainTheTimeIsNotNight() {
            // A narrow window well away from the tested time.
            assertFalse(SpawnService.isNight(15000, 20000, 22000));
        }

        @Test
        void aWindowThatDoesContainTheTimeIsNight() {
            assertTrue(SpawnService.isNight(21000, 20000, 22000));
        }

        @Test
        void aSingleTickWindowMatchesOnlyThatTick() {
            assertTrue(SpawnService.isNight(15000, 15000, 15000));
            assertFalse(SpawnService.isNight(15001, 15000, 15000));
        }
    }

    @Nested
    class WithinCaps {

        // withinCaps(perVictimCount, perPlayerCap, serverSize, serverCap)
        //   == perVictimCount < perPlayerCap && serverSize < serverCap

        @Test
        void bothBelowCapsIsWithin() {
            assertTrue(SpawnService.withinCaps(0, 1, 0, 4));
            assertTrue(SpawnService.withinCaps(0, 2, 3, 4));
        }

        @Test
        void perPlayerAtCapIsNotWithin() {
            // count == cap: the player already has their allotment.
            assertFalse(SpawnService.withinCaps(1, 1, 0, 4));
            assertFalse(SpawnService.withinCaps(2, 2, 0, 4));
        }

        @Test
        void perPlayerAboveCapIsNotWithin() {
            assertFalse(SpawnService.withinCaps(3, 1, 0, 4));
        }

        @Test
        void serverAtCapIsNotWithin() {
            assertFalse(SpawnService.withinCaps(0, 1, 4, 4));
        }

        @Test
        void serverAboveCapIsNotWithin() {
            assertFalse(SpawnService.withinCaps(0, 1, 5, 4));
        }

        @Test
        void serverJustBelowCapIsWithin() {
            assertTrue(SpawnService.withinCaps(0, 1, 3, 4));
        }

        @Test
        void bothAtCapIsNotWithin() {
            assertFalse(SpawnService.withinCaps(1, 1, 4, 4));
        }

        @Test
        void zeroServerCapNeverAdmits() {
            // serverCap == 0 disables spawning entirely: size 0 is not < 0-cap.
            assertFalse(SpawnService.withinCaps(0, 1, 0, 0));
        }
    }
}

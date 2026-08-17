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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the two extracted, Bukkit-free decision/arithmetic seams of
 * {@link FeedingService}: the exploit-closing {@code qualifies} predicate (acceptance check 8)
 * and the per-tick {@code drainThisTick} point arithmetic.
 *
 * <p>The live effects of {@code feedTick} (peek, particles, actual point transfer, stage health)
 * cannot be faithfully mocked without a running server; they are gate-7a obligations documented
 * on {@link FeedingService} and verified against a live client (acceptance checks 8, 9, 10).
 */
class FeedingLogicTest {

    // ---------------------------------------------------------------------------------------
    // qualifies(gazerInReach, requireXp, gazerPoints) — the exploit-closer, full truth table.
    // ---------------------------------------------------------------------------------------

    /** No gazer in reach: never opens, regardless of the other inputs. */
    @Test void noGazerNeverQualifies() {
        assertFalse(FeedingService.qualifies(false, true, 100));
        assertFalse(FeedingService.qualifies(false, true, 0));
        assertFalse(FeedingService.qualifies(false, false, 100));
        assertFalse(FeedingService.qualifies(false, false, 0));
    }

    /** requireXp ON: a gazer with points opens it; a gazer with none does not (the exploit-closer). */
    @Test void requireXpGatesOnPoints() {
        assertTrue(FeedingService.qualifies(true, true, 1));
        assertTrue(FeedingService.qualifies(true, true, 100));
        assertFalse(FeedingService.qualifies(true, true, 0));
    }

    /** A negative point total is treated as "no XP to drain" and keeps it sealed under requireXp. */
    @Test void requireXpTreatsNegativeAsEmpty() {
        assertFalse(FeedingService.qualifies(true, true, -5));
    }

    /** requireXp OFF: an in-reach gazer always opens it, even with zero points. */
    @Test void withoutRequireXpAnyGazerQualifies() {
        assertTrue(FeedingService.qualifies(true, false, 0));
        assertTrue(FeedingService.qualifies(true, false, 100));
        assertTrue(FeedingService.qualifies(true, false, -5));
    }

    // ---------------------------------------------------------------------------------------
    // drainThisTick(perSecond, period) — floor of perSecond*period/20, min 1 while rate > 0.
    // ---------------------------------------------------------------------------------------

    /** A zero (or negative) rate drains nothing; a zero/negative period drains nothing. */
    @Test void zeroRateOrPeriodDrainsNothing() {
        assertEquals(0, FeedingService.drainThisTick(0, 2));
        assertEquals(0, FeedingService.drainThisTick(-4, 2));
        assertEquals(0, FeedingService.drainThisTick(8, 0));
        assertEquals(0, FeedingService.drainThisTick(8, -2));
    }

    /** Exact division cases: perSecond*period is a clean multiple of 20. */
    @Test void exactDivisionMatchesRate() {
        // 8/s over a 20-tick (1s) period -> 8 points.
        assertEquals(8, FeedingService.drainThisTick(8, 20));
        // 20/s over a 2-tick period -> 40/20 = 2 points.
        assertEquals(2, FeedingService.drainThisTick(20, 2));
        // 10/s over a 10-tick period -> 100/20 = 5 points.
        assertEquals(5, FeedingService.drainThisTick(10, 10));
    }

    /** Sub-integer results floor, but a positive rate always drains at least one point so
     * feeding actually progresses (documented minimum). 8/s over a 2-tick period is 0.8 -> 1. */
    @Test void smallRateFloorsToMinimumOne() {
        assertEquals(1, FeedingService.drainThisTick(8, 2));  // 16/20 = 0.8 -> floor 0 -> min 1
        assertEquals(1, FeedingService.drainThisTick(1, 1));  // 1/20  = 0.05 -> min 1
        assertEquals(1, FeedingService.drainThisTick(19, 1)); // 19/20 -> min 1
    }

    /** The floor drops the fractional part above the minimum: 25/s over 2 ticks is 2.5 -> 2. */
    @Test void largerRateFloorsFraction() {
        assertEquals(2, FeedingService.drainThisTick(25, 2));  // 50/20 = 2.5 -> 2
        assertEquals(4, FeedingService.drainThisTick(9, 10));  // 90/20 = 4.5 -> 4
    }

    /** A large rate/period does not overflow int (intermediate widened to long, result clamped
     * into int range). {@code MAX_VALUE*20/20 == MAX_VALUE}; a naive {@code int} multiply would
     * wrap negative and violate the never-negative contract. */
    @Test void largeInputsDoNotOverflowOrGoNegative() {
        assertEquals(Integer.MAX_VALUE, FeedingService.drainThisTick(Integer.MAX_VALUE, 20));
        assertTrue(FeedingService.drainThisTick(Integer.MAX_VALUE, Integer.MAX_VALUE) >= 0);
    }
}

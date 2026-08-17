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
    // drainWithCarry(owedThisTick, carry) — fractional accumulator; whole part out, remainder kept.
    // ---------------------------------------------------------------------------------------

    /** A zero (or negative) owe drains nothing and leaves the carry untouched. */
    @Test void zeroOweDrainsNothingAndKeepsCarry() {
        double[] carry = {0.4};
        assertEquals(0, FeedingService.drainWithCarry(0.0, carry));
        assertEquals(0.4, carry[0], 1e-12);
        assertEquals(0, FeedingService.drainWithCarry(-3.0, carry)); // negative owe clamped to 0
        assertEquals(0.4, carry[0], 1e-12);
    }

    /** An exact-integer owe with no carry drains it whole and leaves a zero remainder. */
    @Test void integerOweDrainsWhole() {
        double[] carry = {0.0};
        assertEquals(2, FeedingService.drainWithCarry(2.0, carry));
        assertEquals(0.0, carry[0], 1e-12);
    }

    /** A single sub-integer owe drains nothing yet and banks the fraction into the carry. */
    @Test void subIntegerOweAccumulatesIntoCarry() {
        double[] carry = {0.0};
        assertEquals(0, FeedingService.drainWithCarry(0.8, carry)); // 0.8 -> floor 0
        assertEquals(0.8, carry[0], 1e-12);
        assertEquals(1, FeedingService.drainWithCarry(0.8, carry)); // 1.6 -> 1, carry 0.6
        assertEquals(0.6, carry[0], 1e-12);
    }

    /**
     * The exploit-relevant guarantee: a rate that does not divide evenly still averages to the
     * exact per-second total. 8/s over a 2-tick feed period owes 0.8 per invocation; across the 10
     * invocations that make up one second the accumulator drains exactly 8 points — not the 10 a
     * per-tick min-1 floor would have produced — and returns the carry to zero.
     */
    @Test void accumulatorAveragesToExactRateOverOneSecond() {
        double owedPerInvocation = 8 * 2 / 20.0; // = 0.8
        double[] carry = {0.0};
        long total = 0;
        for (int i = 0; i < 10; i++) { // 10 invocations * 2 ticks = 20 ticks = 1 second
            total += FeedingService.drainWithCarry(owedPerInvocation, carry);
        }
        assertEquals(8L, total);
        assertEquals(0.0, carry[0], 1e-9);
    }

    /** Another non-dividing rate: 5/s over a 2-tick period owes 0.5; ten invocations drain 5. */
    @Test void accumulatorExactForHalfPointOwe() {
        double[] carry = {0.0};
        long total = 0;
        for (int i = 0; i < 10; i++) {
            total += FeedingService.drainWithCarry(5 * 2 / 20.0, carry); // 0.5 each
        }
        assertEquals(5L, total);
        assertEquals(0.0, carry[0], 1e-9);
    }

    /** An evenly-dividing rate drains the same whole amount every tick with no remainder. */
    @Test void accumulatorSteadyForIntegerOwe() {
        double[] carry = {0.0};
        for (int i = 0; i < 10; i++) {
            assertEquals(2, FeedingService.drainWithCarry(20 * 2 / 20.0, carry)); // 2.0 each
            assertEquals(0.0, carry[0], 1e-12);
        }
    }

    /** The whole part is never negative even if a caller seeds a nonsensical negative carry. */
    @Test void drainIsNeverNegative() {
        double[] carry = {-0.5};
        assertTrue(FeedingService.drainWithCarry(0.2, carry) >= 0);
    }
}

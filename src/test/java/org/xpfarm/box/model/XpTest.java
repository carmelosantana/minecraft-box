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
package org.xpfarm.box.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class XpTest {

    // --- pointsToNext: known vanilla per-level anchors ---
    @Test void toNextAtZero()      { assertEquals(7,   Xp.pointsToNext(0)); }
    @Test void toNextAtOne()       { assertEquals(9,   Xp.pointsToNext(1)); }
    @Test void toNextAtFifteen()   { assertEquals(37,  Xp.pointsToNext(15)); }
    @Test void toNextAtSixteen()   { assertEquals(42,  Xp.pointsToNext(16)); }
    @Test void toNextAtThirty()    { assertEquals(112, Xp.pointsToNext(30)); }
    @Test void toNextAtThirtyOne() { assertEquals(121, Xp.pointsToNext(31)); }

    // --- pointsForLevel: known vanilla cumulative anchors ---
    @Test void forLevelZero()      { assertEquals(0,    Xp.pointsForLevel(0)); }
    @Test void forLevelOne()       { assertEquals(7,    Xp.pointsForLevel(1)); }
    @Test void forLevelSeven()     { assertEquals(91,   Xp.pointsForLevel(7)); }
    @Test void forLevelSixteen()   { assertEquals(352,  Xp.pointsForLevel(16)); }
    // Derived by formula AND consistency (394, NOT the brief's mis-stated 397):
    // 394 - 352 == 42 == pointsToNext(16).
    @Test void forLevelSeventeen() { assertEquals(394,  Xp.pointsForLevel(17)); }
    // 1395 is the canonical vanilla total for level 30 (the "level-30 enchant" figure);
    // verified via consistency: pointsForLevel(31) - 1395 == 112 == pointsToNext(30).
    @Test void forLevelThirty()    { assertEquals(1395, Xp.pointsForLevel(30)); }
    @Test void forLevelThirtyTwo() { assertEquals(1628, Xp.pointsForLevel(32)); }

    /**
     * The authoritative property: the cumulative curve's first difference must equal the
     * per-level curve at every level, across all three piecewise boundaries.
     */
    @Test void cumulativeDeltaEqualsPerLevel() {
        for (int level = 0; level <= 40; level++) {
            assertEquals(Xp.pointsToNext(level),
                    Xp.pointsForLevel(level + 1) - Xp.pointsForLevel(level),
                    "delta mismatch at level " + level);
        }
    }

    /** Cumulative must be strictly monotonic from level 0 upward. */
    @Test void cumulativeIsStrictlyIncreasing() {
        for (int level = 0; level <= 40; level++) {
            assertEquals(true, Xp.pointsForLevel(level + 1) > Xp.pointsForLevel(level),
                    "not increasing at level " + level);
        }
    }

    // --- totalPointsAt ---
    @Test void totalAtLevelZeroProgress() {
        assertEquals(Xp.pointsForLevel(0), Xp.totalPointsAt(0, 0.0f));
    }

    @Test void totalAtHalfBarThirty() {
        // 1395 + round(0.5 * 112) = 1395 + 56
        assertEquals(1451, Xp.totalPointsAt(30, 0.5f));
    }

    @Test void totalAtFullBarIsNextLevel() {
        // progress 1.0 at level L equals the cumulative for L+1
        for (int level = 0; level <= 35; level++) {
            assertEquals(Xp.pointsForLevel(level + 1), Xp.totalPointsAt(level, 1.0f),
                    "full bar mismatch at level " + level);
        }
    }

    @Test void totalRoundsProgress() {
        // level 1: pointsToNext = 9; 0.5 * 9 = 4.5 -> round = 5 (half-up)
        assertEquals(Xp.pointsForLevel(1) + 5, Xp.totalPointsAt(1, 0.5f));
    }

    // --- returnedPoints ---
    @Test void returnedThreeQuarters() { assertEquals(75L, Xp.returnedPoints(100L, 0.75)); }
    @Test void returnedFromZero()      { assertEquals(0L,  Xp.returnedPoints(0L, 0.75)); }
    @Test void returnedFullRatio()     { assertEquals(100L, Xp.returnedPoints(100L, 1.0)); }
    @Test void returnedRoundsHalfUp()  { assertEquals(51L, Xp.returnedPoints(101L, 0.5)); }
    @Test void returnedLargeBank()     { assertEquals(750_000_000L, Xp.returnedPoints(1_000_000_000L, 0.75)); }
}

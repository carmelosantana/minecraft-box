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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class GazeMathTest {
    private final Vector eye = new Vector(0, 0, 0);

    @Test void looksStraightAtTarget() {
        assertTrue(GazeMath.inCone(eye, new Vector(1, 0, 0), new Vector(10, 0, 0), 0.6));
    }
    @Test void targetBehindIsOutOfCone() {
        assertFalse(GazeMath.inCone(eye, new Vector(1, 0, 0), new Vector(-10, 0, 0), 0.6));
    }
    @Test void justInsideConeEdge() {
        // 45deg offset, cos=~0.707 > 0.6 -> inside
        assertTrue(GazeMath.inCone(eye, new Vector(1, 0, 0), new Vector(10, 10, 0), 0.6));
    }
    @Test void justOutsideConeEdge() {
        // ~63deg offset, cos=~0.45 < 0.6 -> outside
        assertFalse(GazeMath.inCone(eye, new Vector(1, 0, 0), new Vector(5, 10, 0), 0.6));
    }
    @Test void degenerateSamePointIsFalse() {
        assertFalse(GazeMath.inCone(eye, new Vector(1, 0, 0), new Vector(0, 0, 0), 0.6));
    }
    @Test void distanceClampTakesTheSmaller() {
        assertEquals(48.0, GazeMath.effectiveMaxDistance(200, 48), 1e-9);
        assertEquals(30.0, GazeMath.effectiveMaxDistance(30, 48), 1e-9);
    }
    @Test void withinDistanceRespectsClamp() {
        assertFalse(GazeMath.withinDistance(eye, new Vector(200, 0, 0), 48));
        assertTrue(GazeMath.withinDistance(eye, new Vector(40, 0, 0), 48));
    }
}

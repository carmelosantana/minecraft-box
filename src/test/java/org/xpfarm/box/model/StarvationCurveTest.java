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
import org.xpfarm.box.config.BoxConfig;
import org.xpfarm.box.testutil.Configs;   // Task 2 adds this tiny builder

class StarvationCurveTest {
    private static final double EPS = 1e-9;
    private final BoxConfig c = Configs.withStarvation(true, 300, 1800, 0.5, 1.5);

    @Test void freshIsNeutral()  { assertEquals(1.0, StarvationCurve.multiplier(0, c), EPS); }
    @Test void beforeOnsetNeutral(){ assertEquals(1.0, StarvationCurve.multiplier(299, c), EPS); }
    @Test void fullyStarvedHitsFloor(){ assertEquals(0.5, StarvationCurve.multiplier(1800, c), EPS); }
    @Test void beyondMaxClamps()  { assertEquals(0.5, StarvationCurve.multiplier(9999, c), EPS); }
    @Test void midpointIsLinear() { assertEquals(0.75, StarvationCurve.multiplier(1050, c), EPS); }
}

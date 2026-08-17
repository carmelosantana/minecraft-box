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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.xpfarm.box.config.StageDef;

class StageTableTest {
    private static StageTable table() {
        return new StageTable(List.of(
                new StageDef(0, 0, 30, 20, 6, false),
                new StageDef(1, 400, 60, 14, 8, false),
                new StageDef(2, 1600, 100, 8, 10, true)));
    }

    @Test void zeroXpIsStageOne()      { assertEquals(0, table().stageFor(0).index()); }
    @Test void justBelowSecondStays()  { assertEquals(0, table().stageFor(399).index()); }
    @Test void atThresholdAdvances()   { assertEquals(1, table().stageFor(400).index()); }
    @Test void hugeXpCapsAtTop()       { assertEquals(2, table().stageFor(999999).index()); }
    @Test void topStageKillsOnContact(){ assertEquals(true, table().stageFor(1600).killsOnContact()); }
}

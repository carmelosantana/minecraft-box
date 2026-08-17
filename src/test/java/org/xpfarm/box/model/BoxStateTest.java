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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoxStateTest {

    private static BoxState fresh() {
        return new BoxState(UUID.randomUUID(), 1000L);
    }

    @Test
    void constructorEstablishesDefaults() {
        UUID id = UUID.randomUUID();
        BoxState s = new BoxState(id, 1000L);
        assertEquals(id, s.creatureId());
        assertEquals(1000L, s.spawnedEpochSecond());
        assertEquals(BoxState.Phase.DORMANT, s.phase());
        assertEquals(0L, s.bankedXp());
        assertEquals(0, s.stageIndex());
        assertNull(s.victim());
        assertFalse(s.isBound());
    }

    @Test
    void bankAccumulates() {
        BoxState s = fresh();
        s.bank(50);
        s.bank(25);
        assertEquals(75L, s.bankedXp());
    }

    @Test
    void bankNeverGoesNegative() {
        BoxState s = fresh();
        s.bank(10);
        s.bank(-100);
        assertEquals(0L, s.bankedXp());
    }

    @Test
    void bankFromZeroWithNegativeStaysZero() {
        BoxState s = fresh();
        s.bank(-5);
        assertEquals(0L, s.bankedXp());
    }

    @Test
    void bankDoesNotUnderflowOnExtremeNegative() {
        BoxState s = fresh();
        s.bank(Long.MIN_VALUE);
        assertEquals(0L, s.bankedXp());
    }

    @Test
    void bankSaturatesRatherThanWrappingOnPositiveOverflow() {
        BoxState s = fresh();
        s.bank(Long.MAX_VALUE);
        s.bank(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, s.bankedXp());
    }

    @Test
    void bindToSetsVictimAndResetsFeedClock() {
        BoxState s = fresh();
        UUID player = UUID.randomUUID();
        s.bindTo(player, 2500L);
        assertEquals(player, s.victim());
        assertTrue(s.isBound());
        assertEquals(2500L, s.lastFedEpochSecond());
    }

    @Test
    void unbindClearsVictim() {
        BoxState s = fresh();
        s.bindTo(UUID.randomUUID(), 2500L);
        s.unbind();
        assertNull(s.victim());
        assertFalse(s.isBound());
    }

    @Test
    void settersMutateServiceDrivenFields() {
        BoxState s = fresh();
        s.setPhase(BoxState.Phase.HUNTING);
        s.setStageIndex(3);
        s.setLastFedEpochSecond(4200L);
        assertEquals(BoxState.Phase.HUNTING, s.phase());
        assertEquals(3, s.stageIndex());
        assertEquals(4200L, s.lastFedEpochSecond());
    }
}

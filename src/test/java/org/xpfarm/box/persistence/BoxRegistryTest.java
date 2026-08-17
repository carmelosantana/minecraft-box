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
package org.xpfarm.box.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.xpfarm.box.model.BoxState;

class BoxRegistryTest {

    private static BoxState state(UUID id) {
        return new BoxState(id, 1000L);
    }

    @Test
    void trackThenGetReturnsTheState() {
        BoxRegistry registry = new BoxRegistry();
        UUID id = UUID.randomUUID();
        BoxState s = state(id);

        registry.track(id, s);

        Optional<BoxState> found = registry.get(id);
        assertTrue(found.isPresent());
        assertEquals(s, found.get());
    }

    @Test
    void getReturnsEmptyForUnknownId() {
        BoxRegistry registry = new BoxRegistry();
        assertTrue(registry.get(UUID.randomUUID()).isEmpty());
    }

    @Test
    void untrackRemovesTheState() {
        BoxRegistry registry = new BoxRegistry();
        UUID id = UUID.randomUUID();
        registry.track(id, state(id));

        registry.untrack(id);

        assertTrue(registry.get(id).isEmpty());
        assertEquals(0, registry.size());
    }

    @Test
    void untrackOfUnknownIdIsNoOp() {
        BoxRegistry registry = new BoxRegistry();
        registry.untrack(UUID.randomUUID());
        assertEquals(0, registry.size());
    }

    @Test
    void sizeReflectsTrackedCount() {
        BoxRegistry registry = new BoxRegistry();
        assertEquals(0, registry.size());

        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        registry.track(a, state(a));
        registry.track(b, state(b));

        assertEquals(2, registry.size());
    }

    @Test
    void allReturnsSnapshotThatDoesNotReflectLaterMutation() {
        BoxRegistry registry = new BoxRegistry();
        UUID a = UUID.randomUUID();
        registry.track(a, state(a));

        Collection<BoxState> snapshot = registry.all();
        assertEquals(1, snapshot.size());

        // Mutating the registry after taking the snapshot must not change the snapshot,
        // and iterating the snapshot while the registry mutates must not throw.
        UUID b = UUID.randomUUID();
        registry.track(b, state(b));
        registry.untrack(a);

        assertEquals(1, snapshot.size());
        // No ConcurrentModificationException even though the map changed underneath.
        for (BoxState ignored : snapshot) {
            // iterate fully
        }
        // Registry itself now reflects the mutations: b added, a removed.
        assertEquals(1, registry.all().size());
        assertTrue(registry.get(b).isPresent());
    }

    @Test
    void allReturnedCollectionIsUnmodifiable() {
        BoxRegistry registry = new BoxRegistry();
        UUID a = UUID.randomUUID();
        registry.track(a, state(a));

        Collection<BoxState> snapshot = registry.all();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(state(UUID.randomUUID())));
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        // Removing through the view must not affect the registry either.
        assertEquals(1, registry.size());
    }

    @Test
    void countForVictimCountsOnlyStatesBoundToThatVictim() {
        BoxRegistry registry = new BoxRegistry();
        UUID victim = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        UUID c1 = UUID.randomUUID();
        BoxState s1 = state(c1);
        s1.bindTo(victim, 1000L);
        registry.track(c1, s1);

        UUID c2 = UUID.randomUUID();
        BoxState s2 = state(c2);
        s2.bindTo(victim, 1000L);
        registry.track(c2, s2);

        UUID c3 = UUID.randomUUID();
        BoxState s3 = state(c3);
        s3.bindTo(other, 1000L);
        registry.track(c3, s3);

        assertEquals(2, registry.countForVictim(victim));
        assertEquals(1, registry.countForVictim(other));
    }

    @Test
    void countForVictimIgnoresUnboundStates() {
        BoxRegistry registry = new BoxRegistry();
        UUID victim = UUID.randomUUID();

        UUID bound = UUID.randomUUID();
        BoxState s = state(bound);
        s.bindTo(victim, 1000L);
        registry.track(bound, s);

        // An unbound (null-victim) state is never counted for any victim.
        UUID unboundId = UUID.randomUUID();
        BoxState unbound = state(unboundId);
        assertFalse(unbound.isBound());
        registry.track(unboundId, unbound);

        assertEquals(1, registry.countForVictim(victim));
    }

    @Test
    void countForVictimIsZeroWhenNoMatch() {
        BoxRegistry registry = new BoxRegistry();
        UUID id = UUID.randomUUID();
        registry.track(id, state(id));
        assertEquals(0, registry.countForVictim(UUID.randomUUID()));
    }
}

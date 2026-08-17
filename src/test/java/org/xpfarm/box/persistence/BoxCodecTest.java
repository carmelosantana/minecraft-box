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

import java.util.Optional;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.xpfarm.box.model.BoxState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless unit tests for {@link BoxCodec}.
 *
 * <p>The container-level {@code write}/{@code read} pair is exercised against a map-backed
 * {@link FakeContainer}. Keys are built through the package-private {@link BoxKeys} factory
 * seam under the plugin's {@code box} namespace, so no live {@code Plugin} is required.
 *
 * <p>What CANNOT run here — deferred to gate 7a runtime verification — is the real
 * {@code CraftPersistentDataContainer} on a spawned entity and tag survival across a chunk
 * save/load and full server restart.
 */
class BoxCodecTest {

    // Package-private headless seam: keys under the "box" namespace without a Plugin.
    private final BoxKeys keys = new BoxKeys(name -> new NamespacedKey("box", name));
    private final BoxCodec codec = new BoxCodec(keys);

    @Test
    void readReturnsEmptyForForeignContainer() {
        // A container that is not one of ours (no id key) yields empty.
        assertTrue(codec.read(new FakeContainer()).isEmpty());
    }

    @Test
    void readReturnsEmptyForContainerWithOtherKeysButNoId() {
        FakeContainer pdc = new FakeContainer();
        pdc.set(keys.bankedXp, PersistentDataType.LONG, 500L);
        assertTrue(codec.read(pdc).isEmpty());
    }

    @Test
    void roundTripsFullyPopulatedBoundState() {
        UUID id = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        BoxState original = new BoxState(id, 1_700_000_000L);
        original.bank(4_242L);
        original.setStageIndex(3);
        original.setPhase(BoxState.Phase.FEEDING);
        original.bindTo(victim, 1_700_009_999L);

        FakeContainer pdc = new FakeContainer();
        codec.write(pdc, original);
        Optional<BoxState> read = codec.read(pdc);

        assertTrue(read.isPresent());
        BoxState restored = read.get();
        assertEquals(id, restored.creatureId());
        assertEquals(1_700_000_000L, restored.spawnedEpochSecond());
        assertEquals(victim, restored.victim());
        assertTrue(restored.isBound());
        assertEquals(4_242L, restored.bankedXp());
        assertEquals(3, restored.stageIndex());
        assertEquals(1_700_009_999L, restored.lastFedEpochSecond());
        assertEquals(BoxState.Phase.FEEDING, restored.phase());
    }

    @Test
    void roundTripsUnboundStateWithNullVictim() {
        UUID id = UUID.randomUUID();
        BoxState original = new BoxState(id, 1_700_000_000L);
        original.bank(90L);
        original.setStageIndex(1);
        original.setPhase(BoxState.Phase.WAITING);
        original.setLastFedEpochSecond(1_700_005_000L);
        // victim stays null (unbound), lastFed still set from a prior meal.

        FakeContainer pdc = new FakeContainer();
        codec.write(pdc, original);
        Optional<BoxState> read = codec.read(pdc);

        assertTrue(read.isPresent());
        BoxState restored = read.get();
        assertEquals(id, restored.creatureId());
        assertNull(restored.victim());
        assertFalse(restored.isBound());
        assertEquals(90L, restored.bankedXp());
        assertEquals(1, restored.stageIndex());
        assertEquals(1_700_005_000L, restored.lastFedEpochSecond());
        assertEquals(BoxState.Phase.WAITING, restored.phase());
    }

    @Test
    void writeDoesNotStoreVictimKeyWhenUnbound() {
        BoxState original = new BoxState(UUID.randomUUID(), 1L);
        FakeContainer pdc = new FakeContainer();
        codec.write(pdc, original);
        assertFalse(pdc.has(keys.victim));
    }

    @Test
    void writeClearsStaleVictimWhenRewrittenUnbound() {
        UUID id = UUID.randomUUID();
        BoxState bound = new BoxState(id, 1L);
        bound.bindTo(UUID.randomUUID(), 5L);
        FakeContainer pdc = new FakeContainer();
        codec.write(pdc, bound);
        assertTrue(pdc.has(keys.victim));

        // Rewriting an unbound state must not leave the old victim behind.
        BoxState unbound = new BoxState(id, 1L);
        codec.write(pdc, unbound);
        assertFalse(pdc.has(keys.victim));
        assertNull(codec.read(pdc).orElseThrow().victim());
    }

    @Test
    void roundTripsEveryPhaseValue() {
        for (BoxState.Phase phase : BoxState.Phase.values()) {
            BoxState original = new BoxState(UUID.randomUUID(), 1L);
            original.setPhase(phase);
            FakeContainer pdc = new FakeContainer();
            codec.write(pdc, original);
            assertEquals(phase, codec.read(pdc).orElseThrow().phase());
        }
    }
}

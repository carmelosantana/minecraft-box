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

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.xpfarm.box.model.BoxState;

/**
 * In-memory index of tracked Box creatures, keyed by creature {@link UUID}.
 *
 * <p>This is a pure data structure with no Bukkit dependency: it holds the mutable {@link BoxState}
 * for every live creature so the tick loop and services can find state by id in O(1). The live
 * {@code Shulker} entity handle is <strong>not</strong> stored here; services look it up separately
 * from the creature id.
 *
 * <p>Backed by a {@link ConcurrentHashMap}, so reads and writes are safe to interleave with the tick
 * loop iterating {@link #all()}.
 */
public final class BoxRegistry {

    private final ConcurrentMap<UUID, BoxState> states = new ConcurrentHashMap<>();

    /**
     * Registers a creature's state under its id, replacing any existing entry.
     *
     * @param id the creature identifier
     * @param s the state to track
     */
    public void track(UUID id, BoxState s) {
        states.put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(s, "s"));
    }

    /**
     * Removes the creature with the given id, if present.
     *
     * @param id the creature identifier
     */
    public void untrack(UUID id) {
        states.remove(id);
    }

    /**
     * Looks up the state for a creature id.
     *
     * @param id the creature identifier
     * @return the tracked state, or empty when the id is unknown
     */
    public Optional<BoxState> get(UUID id) {
        return Optional.ofNullable(states.get(id));
    }

    /**
     * Returns an immutable snapshot of every tracked state, safe to iterate while the tick loop
     * mutates the registry.
     *
     * <p>The returned collection is a defensive copy: it never throws {@link
     * java.util.ConcurrentModificationException} when the registry changes afterwards, and callers
     * cannot add to or remove from the registry through it. Note the {@link BoxState} elements are
     * still the live, mutable objects.
     *
     * @return an unmodifiable copy of the current states
     */
    public Collection<BoxState> all() {
        return List.copyOf(states.values());
    }

    /**
     * Counts tracked states bound to exactly the given victim.
     *
     * <p>Unbound states (those with a {@code null} victim) are never counted for any victim.
     *
     * @param victim the victim identifier
     * @return the number of tracked states whose victim equals {@code victim}
     */
    public long countForVictim(UUID victim) {
        return states.values().stream()
                .filter(BoxState::isBound)
                .filter(s -> Objects.equals(s.victim(), victim))
                .count();
    }

    /**
     * @return the number of tracked creatures
     */
    public int size() {
        return states.size();
    }
}

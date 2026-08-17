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

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.xpfarm.box.model.BoxState;

/**
 * Persists {@link BoxState} to and from a creature entity's {@link PersistentDataContainer},
 * using the shared {@link BoxKeys}. The entity's own PDC is the plugin's source of truth, so
 * this round-trip must be lossless and survive a server restart.
 *
 * <p>Layout on the creature's PDC (only {@code LONG}/{@code INTEGER}/{@code STRING} are used):
 * <ul>
 *   <li>{@code id} — STRING creature UUID; the identity marker (present iff this container is
 *       one of ours). Its absence makes {@link #read} return empty.</li>
 *   <li>{@code spawned} — LONG epoch second</li>
 *   <li>{@code victim} — STRING victim UUID; absent when unbound</li>
 *   <li>{@code banked_xp} — LONG experience points</li>
 *   <li>{@code stage} — INTEGER stage index</li>
 *   <li>{@code last_fed} — LONG epoch second</li>
 *   <li>{@code phase} — STRING {@link BoxState.Phase} name</li>
 * </ul>
 */
public final class BoxCodec {

    private final BoxKeys keys;

    public BoxCodec(BoxKeys keys) {
        this.keys = Objects.requireNonNull(keys, "keys");
    }

    /**
     * Writes the creature's full state onto the container, tagging it as one of ours via the
     * {@code id} key. When the state is unbound the {@code victim} key is removed rather than
     * left stale.
     */
    public void write(PersistentDataContainer pdc, BoxState s) {
        pdc.set(keys.id, PersistentDataType.STRING, s.creatureId().toString());
        pdc.set(keys.spawned, PersistentDataType.LONG, s.spawnedEpochSecond());
        pdc.set(keys.bankedXp, PersistentDataType.LONG, s.bankedXp());
        pdc.set(keys.stage, PersistentDataType.INTEGER, s.stageIndex());
        pdc.set(keys.lastFed, PersistentDataType.LONG, s.lastFedEpochSecond());
        pdc.set(keys.phase, PersistentDataType.STRING, s.phase().name());
        UUID victim = s.victim();
        if (victim != null) {
            pdc.set(keys.victim, PersistentDataType.STRING, victim.toString());
        } else {
            pdc.remove(keys.victim);
        }
    }

    /**
     * Restores a {@link BoxState} from the container.
     *
     * @return empty when the {@code id} marker key is absent (the container is not one of ours)
     */
    public Optional<BoxState> read(PersistentDataContainer pdc) {
        String rawId = pdc.get(keys.id, PersistentDataType.STRING);
        if (rawId == null) {
            return Optional.empty();
        }
        UUID creatureId = UUID.fromString(rawId);
        long spawned = pdc.getOrDefault(keys.spawned, PersistentDataType.LONG, 0L);

        BoxState s = new BoxState(creatureId, spawned);
        s.bank(pdc.getOrDefault(keys.bankedXp, PersistentDataType.LONG, 0L));
        s.setStageIndex(pdc.getOrDefault(keys.stage, PersistentDataType.INTEGER, 0));
        s.setPhase(BoxState.Phase.valueOf(
                pdc.getOrDefault(keys.phase, PersistentDataType.STRING, BoxState.Phase.DORMANT.name())));

        long lastFed = pdc.getOrDefault(keys.lastFed, PersistentDataType.LONG, 0L);
        String rawVictim = pdc.get(keys.victim, PersistentDataType.STRING);
        if (rawVictim != null) {
            // bindTo sets both the victim and the feed baseline in one shot.
            s.bindTo(UUID.fromString(rawVictim), lastFed);
        } else {
            s.setLastFedEpochSecond(lastFed);
        }
        return Optional.of(s);
    }
}

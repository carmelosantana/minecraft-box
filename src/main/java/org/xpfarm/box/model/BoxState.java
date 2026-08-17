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

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Mutable in-memory state for a single Box creature.
 *
 * <p>This is a plain data holder with no Bukkit dependency: it stores what the creature knows about
 * itself between ticks, and is round-tripped to persistence by the codec (Task 7). It deliberately
 * encodes <strong>no</strong> behaviour beyond two invariants — banked experience never goes
 * negative, and {@link #isBound()} tracks the presence of a victim. Everything else (phase
 * transitions, stage recomputation via {@code StageTable}) is driven by the tick loop and services,
 * which read and write these fields through the accessors below.
 */
public final class BoxState {

    /** The lifecycle phase a creature is in; transitions are driven externally by the service. */
    public enum Phase {
        /** Not yet activated: no victim, waiting to be triggered. */
        DORMANT,
        /** Actively closing on a victim. */
        HUNTING,
        /** Held still because it is currently observed. */
        FROZEN,
        /** Draining experience from a victim within feed range. */
        FEEDING,
        /** Between meals: bound but idle. */
        WAITING
    }

    private final UUID creatureId;
    private final long spawnedEpochSecond;

    private @Nullable UUID victim;
    private long bankedXp;
    private int stageIndex;
    private long lastFedEpochSecond;
    private Phase phase;

    /**
     * Creates a fresh dormant creature with no victim, no banked experience, and stage zero.
     *
     * @param creatureId the stable identifier of this creature entity
     * @param spawnedEpochSecond the epoch second at which the creature came into being
     */
    public BoxState(UUID creatureId, long spawnedEpochSecond) {
        this.creatureId = creatureId;
        this.spawnedEpochSecond = spawnedEpochSecond;
        this.phase = Phase.DORMANT;
        this.bankedXp = 0L;
        this.stageIndex = 0;
        this.victim = null;
        this.lastFedEpochSecond = 0L;
    }

    /**
     * Adds experience points to the bank, flooring the result at zero.
     *
     * <p>A negative {@code points} debits the bank but can never drive it below zero, and the clamp
     * is underflow-safe: even {@link Long#MIN_VALUE} lands the bank at zero rather than wrapping.
     *
     * @param points the points to add (may be negative to debit)
     */
    public void bank(long points) {
        long sum;
        try {
            sum = Math.addExact(this.bankedXp, points);
        } catch (ArithmeticException overflow) {
            // Saturate rather than wrap: a huge credit pins high, a huge debit floors low.
            sum = points > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        this.bankedXp = Math.max(0L, sum);
    }

    /**
     * Binds this creature to a victim and resets the feed clock.
     *
     * <p>Sets the victim and records {@code nowSecond} as the last-fed moment so the starvation
     * timer starts fresh from the bind. Phase transitions are left to the service.
     *
     * @param player the victim's identifier
     * @param nowSecond the current epoch second, taken as the fresh feed baseline
     */
    public void bindTo(UUID player, long nowSecond) {
        this.victim = player;
        this.lastFedEpochSecond = nowSecond;
    }

    /** Clears the current victim; the creature is no longer bound. */
    public void unbind() {
        this.victim = null;
    }

    /**
     * @return {@code true} while a victim is set
     */
    public boolean isBound() {
        return this.victim != null;
    }

    /**
     * @return the stable identifier of this creature entity
     */
    public UUID creatureId() {
        return this.creatureId;
    }

    /**
     * @return the epoch second at which the creature spawned
     */
    public long spawnedEpochSecond() {
        return this.spawnedEpochSecond;
    }

    /**
     * @return the current victim, or {@code null} when unbound
     */
    public @Nullable UUID victim() {
        return this.victim;
    }

    /**
     * @return the banked experience points (never negative)
     */
    public long bankedXp() {
        return this.bankedXp;
    }

    /**
     * @return the zero-based index of the creature's current growth stage
     */
    public int stageIndex() {
        return this.stageIndex;
    }

    /**
     * Sets the current growth stage index. The service recomputes this from {@code StageTable}.
     *
     * @param stageIndex the new zero-based stage index
     */
    public void setStageIndex(int stageIndex) {
        this.stageIndex = stageIndex;
    }

    /**
     * @return the epoch second the creature last fed
     */
    public long lastFedEpochSecond() {
        return this.lastFedEpochSecond;
    }

    /**
     * Sets the last-fed epoch second, advancing the starvation baseline.
     *
     * @param lastFedEpochSecond the new last-fed epoch second
     */
    public void setLastFedEpochSecond(long lastFedEpochSecond) {
        this.lastFedEpochSecond = lastFedEpochSecond;
    }

    /**
     * @return the creature's current lifecycle phase
     */
    public Phase phase() {
        return this.phase;
    }

    /**
     * Sets the creature's lifecycle phase. Transitions are driven by the tick loop.
     *
     * @param phase the new phase
     */
    public void setPhase(Phase phase) {
        this.phase = phase;
    }
}

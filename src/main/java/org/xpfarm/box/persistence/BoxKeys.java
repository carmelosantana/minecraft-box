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

import java.util.function.Function;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Central registry of every {@link NamespacedKey} the plugin uses, so all tasks share the
 * exact same keys.
 *
 * <p>Creature and item identity is carried exclusively by these PDC keys — never by display
 * name or CustomModelData — so Bedrock players joining through Geyser get identical behavior.
 * The entity's own PDC (keyed by {@link #id}) is the plugin's source of truth for creature
 * state.
 */
public final class BoxKeys {

    /** Entity tag + marker: the creature's stable id; presence means "one of ours". */
    public final NamespacedKey id;
    /** Persistence: UUID of the creature's current victim (absent when unbound). */
    public final NamespacedKey victim;
    /** Persistence: banked experience points (LONG). */
    public final NamespacedKey bankedXp;
    /** Persistence: zero-based growth stage index (INTEGER). */
    public final NamespacedKey stage;
    /** Persistence: epoch second the creature last fed (LONG). */
    public final NamespacedKey lastFed;
    /** Persistence: epoch second the creature spawned (LONG). */
    public final NamespacedKey spawned;
    /** Persistence: lifecycle phase name (STRING). */
    public final NamespacedKey phase;
    /** Item tag: banked experience points carried by a dropped artifact (LONG). */
    public final NamespacedKey artifactXp;
    /** Item tag: marks an item stack as a Box artifact. */
    public final NamespacedKey artifactMarker;

    /**
     * Builds the keys from the live plugin instance (the normal runtime path).
     */
    public BoxKeys(Plugin plugin) {
        this(name -> new NamespacedKey(plugin, name));
    }

    /**
     * Seam used by headless unit tests, which cannot obtain a live {@link Plugin} instance:
     * keys are produced by the supplied factory instead.
     */
    BoxKeys(Function<String, NamespacedKey> factory) {
        this.id = factory.apply("id");
        this.victim = factory.apply("victim");
        this.bankedXp = factory.apply("banked_xp");
        this.stage = factory.apply("stage");
        this.lastFed = factory.apply("last_fed");
        this.spawned = factory.apply("spawned");
        this.phase = factory.apply("phase");
        this.artifactXp = factory.apply("artifact_xp");
        this.artifactMarker = factory.apply("artifact_marker");
    }
}

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
package org.xpfarm.box.testutil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.xpfarm.box.config.BoxConfig;
import org.xpfarm.box.config.BoxSound;
import org.xpfarm.box.config.StageDef;

/**
 * Test-only builder for fully-populated, valid {@link BoxConfig} instances constructed
 * directly (no Bukkit config parsing, no running server). Every pure test reuses this so it
 * never has to spell out all of {@code BoxConfig}'s components. The shipped defaults mirror
 * {@code BoxConfig.from(...)}; override helpers replace just the components a test cares about.
 */
public final class Configs {

    private Configs() {
    }

    /** The shipped three-stage growth table, matching {@code BoxConfig.from}'s fallback. */
    public static List<StageDef> defaultStages() {
        return List.of(
                new StageDef(1, 0L, 30.0, 20, 6.0, false),
                new StageDef(2, 400L, 60.0, 14, 8.0, false),
                new StageDef(3, 1600L, 100.0, 8, 10.0, true));
    }

    /** A minimal valid audio map; {@code Material.ECHO_SHARD} name resolution is never touched. */
    private static Map<String, BoxSound> defaultSounds() {
        Map<String, BoxSound> sounds = new LinkedHashMap<>();
        sounds.put("dormant-ambience", new BoxSound("BLOCK_SCULK_SENSOR_CLICKING", 0.6f, 0.5f, 0));
        return sounds;
    }

    /** A fully-populated, valid config with every component at a sane default. */
    public static BoxConfig defaults() {
        return new BoxConfig(
                false,   // spawnEnabled
                300,     // checkIntervalSeconds
                0.08,    // chance
                24,      // minDistance
                48,      // maxDistance
                1,       // perPlayerCap
                4,       // serverCap
                true,    // requireSkyAccess
                13000,   // nightStart
                23000,   // nightEnd
                128,     // minDistanceFromWorldSpawn
                0.6,     // fovCosine
                48,      // gazeMaxDistance
                10,      // lockOnTicks
                true,    // ignoreCreative
                true,    // ignoreSpectator
                1,       // maxStepUp
                3,       // maxStepDown
                24,      // maxClimbHeight
                true,    // allowCeilingTraversal
                8,       // xpPerSecond
                true,    // requireXpToOpen
                defaultStages(),
                true,    // starvationEnabled
                300,     // onsetSeconds
                1800,    // maxSeconds
                0.5,     // stepIntervalMultiplier
                1.5,     // volumeMultiplier
                true,    // disorientationEnabled
                120,     // nauseaTicks
                100,     // darknessTicks
                1.5,     // contactRadius
                200,     // contactBlindnessTicks
                300,     // contactNauseaTicks
                defaultSounds(),
                Material.ECHO_SHARD,
                "Cursed Artifact",
                0.75,    // xpReturnRatio
                true,    // curseIntegration
                30,      // offlineDormantMinutes
                0,       // maxLifetimeHours
                true);   // unbindOnVictimDeath
    }

    /** A config identical to {@link #defaults()} but with the starvation group overridden. */
    public static BoxConfig withStarvation(boolean enabled, int onsetSeconds, int maxSeconds,
            double stepIntervalMultiplier, double volumeMultiplier) {
        BoxConfig d = defaults();
        return new BoxConfig(
                d.spawnEnabled(), d.checkIntervalSeconds(), d.chance(), d.minDistance(),
                d.maxDistance(), d.perPlayerCap(), d.serverCap(), d.requireSkyAccess(),
                d.nightStart(), d.nightEnd(), d.minDistanceFromWorldSpawn(), d.fovCosine(),
                d.gazeMaxDistance(), d.lockOnTicks(), d.ignoreCreative(), d.ignoreSpectator(),
                d.maxStepUp(), d.maxStepDown(), d.maxClimbHeight(), d.allowCeilingTraversal(),
                d.xpPerSecond(), d.requireXpToOpen(), d.stages(),
                enabled, onsetSeconds, maxSeconds, stepIntervalMultiplier, volumeMultiplier,
                d.disorientationEnabled(), d.nauseaTicks(), d.darknessTicks(), d.contactRadius(),
                d.contactBlindnessTicks(), d.contactNauseaTicks(), d.sounds(),
                d.artifactMaterial(), d.artifactName(), d.xpReturnRatio(), d.curseIntegration(),
                d.offlineDormantMinutes(), d.maxLifetimeHours(), d.unbindOnVictimDeath());
    }

    /** A config identical to {@link #defaults()} but with the growth stage table overridden. */
    public static BoxConfig withStages(List<StageDef> stages) {
        BoxConfig d = defaults();
        return new BoxConfig(
                d.spawnEnabled(), d.checkIntervalSeconds(), d.chance(), d.minDistance(),
                d.maxDistance(), d.perPlayerCap(), d.serverCap(), d.requireSkyAccess(),
                d.nightStart(), d.nightEnd(), d.minDistanceFromWorldSpawn(), d.fovCosine(),
                d.gazeMaxDistance(), d.lockOnTicks(), d.ignoreCreative(), d.ignoreSpectator(),
                d.maxStepUp(), d.maxStepDown(), d.maxClimbHeight(), d.allowCeilingTraversal(),
                d.xpPerSecond(), d.requireXpToOpen(), stages,
                d.starvationEnabled(), d.onsetSeconds(), d.maxSeconds(),
                d.stepIntervalMultiplier(), d.volumeMultiplier(), d.disorientationEnabled(),
                d.nauseaTicks(), d.darknessTicks(), d.contactRadius(), d.contactBlindnessTicks(),
                d.contactNauseaTicks(), d.sounds(), d.artifactMaterial(), d.artifactName(),
                d.xpReturnRatio(), d.curseIntegration(), d.offlineDormantMinutes(),
                d.maxLifetimeHours(), d.unbindOnVictimDeath());
    }
}

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
package org.xpfarm.box.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Immutable, validated snapshot of {@code config.yml}.
 *
 * <p>Pure logic: no file I/O and no server calls beyond {@link Material} and {@link BoxSound}
 * name resolution. Build one with {@link #from(ConfigurationSection)} from the plugin's
 * {@code FileConfiguration} (or any {@code ConfigurationSection}) the caller loaded. Every
 * downstream component reads its values from this record.
 *
 * <p>Fields mirror {@code config.yml} group for group: spawn, gaze, movement, feeding, growth
 * stages, starvation, disorientation, contact, audio, artifact, and lifetime.
 *
 * @param spawnEnabled whether natural spawning is on; ships {@code false}
 * @param checkIntervalSeconds seconds between spawn rolls per eligible player, {@code >= 0}
 * @param chance probability one roll spawns a creature, {@code 0.0 <= chance <= 1.0}
 * @param minDistance nearest spawn distance from the player in blocks, {@code >= 0}
 * @param maxDistance farthest spawn distance from the player in blocks,
 *     {@code >= minDistance}
 * @param perPlayerCap live creatures allowed per player, {@code >= 1}
 * @param serverCap live creatures allowed server-wide, {@code >= 0}
 * @param requireSkyAccess whether the spawn block needs direct sky access
 * @param nightStart overworld night window start in ticks, {@code < nightEnd}
 * @param nightEnd overworld night window end in ticks
 * @param minDistanceFromWorldSpawn blocks of exclusion around world spawn, {@code >= 0}
 * @param fovCosine cosine of the gaze cone half-angle, {@code -1.0 <= fovCosine <= 1.0}
 * @param gazeMaxDistance max distance a gaze can freeze the creature in blocks, {@code >= 0}
 * @param lockOnTicks continuous gaze ticks needed to bind the creature, {@code >= 0}
 * @param ignoreCreative whether creative-mode players cannot freeze or bind it
 * @param ignoreSpectator whether spectator-mode players cannot freeze or bind it
 * @param maxStepUp blocks the creature steps up while unobserved, {@code >= 0}
 * @param maxStepDown blocks the creature steps down while unobserved, {@code >= 0}
 * @param maxClimbHeight max vertical face height it climbs in blocks, {@code >= 0}
 * @param allowCeilingTraversal whether it may traverse ceilings
 * @param xpPerSecond experience points drained per second, {@code >= 0}
 * @param requireXpToOpen whether it stays sealed for a watcher with no XP to drain
 * @param stages growth stages in ascending threshold order, non-empty
 * @param starvationEnabled whether the starvation curve applies
 * @param onsetSeconds seconds unfed before starvation begins, {@code >= 0}
 * @param maxSeconds seconds unfed at which multipliers are fully applied, {@code >= 0}
 * @param stepIntervalMultiplier step-interval factor at full starvation, {@code >= 0}
 * @param volumeMultiplier volume factor at full starvation, {@code >= 0}
 * @param disorientationEnabled whether the one-shot bind disorientation applies
 * @param nauseaTicks nausea duration on bind in ticks, {@code >= 0}
 * @param darknessTicks darkness duration on bind in ticks, {@code >= 0}
 * @param contactRadius distance counted as reaching a victim in blocks, {@code >= 0}
 * @param contactBlindnessTicks blindness on non-lethal contact in ticks, {@code >= 0}
 * @param contactNauseaTicks nausea on non-lethal contact in ticks, {@code >= 0}
 * @param sounds audio events by config key, in file order
 * @param artifactMaterial dropped artifact material
 * @param artifactName artifact display name
 * @param xpReturnRatio fraction of banked XP returned on consume, {@code 0.0 <= r <= 1.0}
 * @param curseIntegration whether the artifact gains TheCurse behavior when present
 * @param offlineDormantMinutes minutes waited for an offline victim, {@code >= 0}
 * @param maxLifetimeHours hours before forced despawn; {@code 0} disables, {@code >= 0}
 * @param unbindOnVictimDeath whether victim death releases the binding
 */
public record BoxConfig(
        boolean spawnEnabled,
        int checkIntervalSeconds,
        double chance,
        int minDistance,
        int maxDistance,
        int perPlayerCap,
        int serverCap,
        boolean requireSkyAccess,
        int nightStart,
        int nightEnd,
        int minDistanceFromWorldSpawn,
        double fovCosine,
        int gazeMaxDistance,
        int lockOnTicks,
        boolean ignoreCreative,
        boolean ignoreSpectator,
        int maxStepUp,
        int maxStepDown,
        int maxClimbHeight,
        boolean allowCeilingTraversal,
        int xpPerSecond,
        boolean requireXpToOpen,
        List<StageDef> stages,
        boolean starvationEnabled,
        int onsetSeconds,
        int maxSeconds,
        double stepIntervalMultiplier,
        double volumeMultiplier,
        boolean disorientationEnabled,
        int nauseaTicks,
        int darknessTicks,
        double contactRadius,
        int contactBlindnessTicks,
        int contactNauseaTicks,
        Map<String, BoxSound> sounds,
        Material artifactMaterial,
        String artifactName,
        double xpReturnRatio,
        boolean curseIntegration,
        int offlineDormantMinutes,
        int maxLifetimeHours,
        boolean unbindOnVictimDeath) {

    /**
     * Defensive copies so the record is deeply immutable regardless of what is passed in.
     * {@code sounds} preserves iteration order ({@code Map.copyOf} would not) to keep the
     * audio keys in their {@code config.yml} order.
     */
    public BoxConfig {
        stages = List.copyOf(stages);
        sounds = Collections.unmodifiableMap(new LinkedHashMap<>(sounds));
    }

    /**
     * Reads and validates a configuration section (typically the plugin's
     * {@code FileConfiguration}). Missing keys fall back to the shipped defaults.
     *
     * @param root the configuration root to read from
     * @return the validated, immutable configuration
     * @throws IllegalArgumentException if any value is outside its documented range or names
     *     an unknown material or sound; the message names the offending key and value
     */
    public static BoxConfig from(ConfigurationSection root) {
        boolean spawnEnabled = root.getBoolean("spawn.enabled", false);
        int checkIntervalSeconds = root.getInt("spawn.check-interval-seconds", 300);
        double chance = root.getDouble("spawn.chance", 0.08);
        int minDistance = root.getInt("spawn.min-distance", 24);
        int maxDistance = root.getInt("spawn.max-distance", 48);
        int perPlayerCap = root.getInt("spawn.per-player-cap", 1);
        int serverCap = root.getInt("spawn.server-cap", 4);
        boolean requireSkyAccess = root.getBoolean("spawn.require-sky-access", true);
        int nightStart = root.getInt("spawn.night-start", 13000);
        int nightEnd = root.getInt("spawn.night-end", 23000);
        int minDistanceFromWorldSpawn = root.getInt("spawn.min-distance-from-world-spawn", 128);

        double fovCosine = root.getDouble("gaze.fov-cosine", 0.6);
        int gazeMaxDistance = root.getInt("gaze.max-distance", 48);
        int lockOnTicks = root.getInt("gaze.lock-on-ticks", 10);
        boolean ignoreCreative = root.getBoolean("gaze.ignore-creative", true);
        boolean ignoreSpectator = root.getBoolean("gaze.ignore-spectator", true);

        int maxStepUp = root.getInt("movement.max-step-up", 1);
        int maxStepDown = root.getInt("movement.max-step-down", 3);
        int maxClimbHeight = root.getInt("movement.max-climb-height", 24);
        boolean allowCeilingTraversal = root.getBoolean("movement.allow-ceiling-traversal", true);

        int xpPerSecond = root.getInt("feeding.xp-per-second", 8);
        boolean requireXpToOpen = root.getBoolean("feeding.require-xp-to-open", true);

        boolean starvationEnabled = root.getBoolean("starvation.enabled", true);
        int onsetSeconds = root.getInt("starvation.onset-seconds", 300);
        int maxSeconds = root.getInt("starvation.max-seconds", 1800);
        double stepIntervalMultiplier = root.getDouble("starvation.step-interval-multiplier", 0.5);
        double volumeMultiplier = root.getDouble("starvation.volume-multiplier", 1.5);

        boolean disorientationEnabled = root.getBoolean("disorientation.enabled", true);
        int nauseaTicks = root.getInt("disorientation.nausea-ticks", 120);
        int darknessTicks = root.getInt("disorientation.darkness-ticks", 100);

        double contactRadius = root.getDouble("contact.radius", 1.5);
        int contactBlindnessTicks = root.getInt("contact.blindness-ticks", 200);
        int contactNauseaTicks = root.getInt("contact.nausea-ticks", 300);

        Material artifactMaterial = readMaterial(root, "artifact.material", "ECHO_SHARD");
        String artifactName = root.getString("artifact.display-name", "Cursed Artifact");
        double xpReturnRatio = root.getDouble("artifact.xp-return-ratio", 0.75);
        boolean curseIntegration = root.getBoolean("artifact.curse-integration", true);

        int offlineDormantMinutes = root.getInt("lifetime.offline-dormant-minutes", 30);
        int maxLifetimeHours = root.getInt("lifetime.max-lifetime-hours", 0);
        boolean unbindOnVictimDeath = root.getBoolean("lifetime.unbind-on-victim-death", true);

        // Range checks. Ordered so the scalar validations run before the stage assembly, so a
        // bad scalar reports its own key rather than being masked by an unrelated stage error.
        check(chance >= 0.0 && chance <= 1.0, "spawn.chance", chance, "must be 0.0 - 1.0");
        check(checkIntervalSeconds >= 0, "spawn.check-interval-seconds", checkIntervalSeconds,
                "must be >= 0");
        check(minDistance >= 0, "spawn.min-distance", minDistance, "must be >= 0");
        check(maxDistance >= 0, "spawn.max-distance", maxDistance, "must be >= 0");
        check(minDistance <= maxDistance, "spawn.min-distance", minDistance,
                "must be <= spawn.max-distance (" + maxDistance + ")");
        check(perPlayerCap >= 1, "spawn.per-player-cap", perPlayerCap, "must be >= 1");
        check(serverCap >= 0, "spawn.server-cap", serverCap, "must be >= 0");
        check(nightStart >= 0, "spawn.night-start", nightStart, "must be >= 0");
        check(nightStart < nightEnd, "spawn.night-start", nightStart,
                "must be < spawn.night-end (" + nightEnd + ")");
        check(minDistanceFromWorldSpawn >= 0, "spawn.min-distance-from-world-spawn",
                minDistanceFromWorldSpawn, "must be >= 0");

        check(fovCosine >= -1.0 && fovCosine <= 1.0, "gaze.fov-cosine", fovCosine,
                "must be -1.0 - 1.0");
        check(gazeMaxDistance >= 0, "gaze.max-distance", gazeMaxDistance, "must be >= 0");
        check(lockOnTicks >= 0, "gaze.lock-on-ticks", lockOnTicks, "must be >= 0");

        check(maxStepUp >= 0, "movement.max-step-up", maxStepUp, "must be >= 0");
        check(maxStepDown >= 0, "movement.max-step-down", maxStepDown, "must be >= 0");
        check(maxClimbHeight >= 0, "movement.max-climb-height", maxClimbHeight, "must be >= 0");

        check(xpPerSecond >= 0, "feeding.xp-per-second", xpPerSecond, "must be >= 0");

        check(onsetSeconds >= 0, "starvation.onset-seconds", onsetSeconds, "must be >= 0");
        check(maxSeconds >= 0, "starvation.max-seconds", maxSeconds, "must be >= 0");
        check(stepIntervalMultiplier >= 0, "starvation.step-interval-multiplier",
                stepIntervalMultiplier, "must be >= 0");
        check(volumeMultiplier >= 0, "starvation.volume-multiplier", volumeMultiplier,
                "must be >= 0");

        check(nauseaTicks >= 0, "disorientation.nausea-ticks", nauseaTicks, "must be >= 0");
        check(darknessTicks >= 0, "disorientation.darkness-ticks", darknessTicks, "must be >= 0");

        check(contactRadius >= 0, "contact.radius", contactRadius, "must be >= 0");
        check(contactBlindnessTicks >= 0, "contact.blindness-ticks", contactBlindnessTicks,
                "must be >= 0");
        check(contactNauseaTicks >= 0, "contact.nausea-ticks", contactNauseaTicks,
                "must be >= 0");

        check(xpReturnRatio >= 0.0 && xpReturnRatio <= 1.0, "artifact.xp-return-ratio",
                xpReturnRatio, "must be 0.0 - 1.0");

        check(offlineDormantMinutes >= 0, "lifetime.offline-dormant-minutes",
                offlineDormantMinutes, "must be >= 0");
        check(maxLifetimeHours >= 0, "lifetime.max-lifetime-hours", maxLifetimeHours,
                "must be >= 0");

        Map<String, BoxSound> sounds = readSounds(root);
        List<StageDef> stages = readStages(root);

        return new BoxConfig(spawnEnabled, checkIntervalSeconds, chance, minDistance, maxDistance,
                perPlayerCap, serverCap, requireSkyAccess, nightStart, nightEnd,
                minDistanceFromWorldSpawn, fovCosine, gazeMaxDistance, lockOnTicks, ignoreCreative,
                ignoreSpectator, maxStepUp, maxStepDown, maxClimbHeight, allowCeilingTraversal,
                xpPerSecond, requireXpToOpen, stages, starvationEnabled, onsetSeconds, maxSeconds,
                stepIntervalMultiplier, volumeMultiplier, disorientationEnabled, nauseaTicks,
                darknessTicks, contactRadius, contactBlindnessTicks, contactNauseaTicks, sounds,
                artifactMaterial, artifactName, xpReturnRatio, curseIntegration,
                offlineDormantMinutes, maxLifetimeHours, unbindOnVictimDeath);
    }

    /**
     * Reads the {@code audio.*} events, one {@link BoxSound} per key with its shipped default,
     * and resolves each name so an unknown sound fails on load rather than at play time.
     */
    private static Map<String, BoxSound> readSounds(ConfigurationSection root) {
        ConfigurationSection audio = root.getConfigurationSection("audio");
        Map<String, BoxSound> sounds = new LinkedHashMap<>();
        putSound(sounds, audio, "dormant-ambience", "BLOCK_SCULK_SENSOR_CLICKING", 0.6f, 0.5f);
        putSound(sounds, audio, "lock-on-sting", "BLOCK_SCULK_SHRIEKER_SHRIEK", 1.0f, 0.7f);
        putSound(sounds, audio, "proximity-pulse", "ENTITY_WARDEN_HEARTBEAT", 0.8f, 0.8f);
        putSound(sounds, audio, "movement", "BLOCK_SCULK_SPREAD", 0.3f, 0.5f);
        putSound(sounds, audio, "feeding", "ENTITY_EXPERIENCE_ORB_PICKUP", 0.7f, 0.5f);
        putSound(sounds, audio, "opening", "ENTITY_SHULKER_OPEN", 0.9f, 0.4f);
        putSound(sounds, audio, "death", "ENTITY_WARDEN_DEATH", 1.0f, 0.6f);
        putSound(sounds, audio, "haunting", "MUSIC_DISC_11", 0.5f, 1.0f);
        return sounds;
    }

    private static void putSound(Map<String, BoxSound> out, ConfigurationSection audio, String key,
            String defSound, float defVol, float defPitch) {
        ConfigurationSection s = audio == null ? null : audio.getConfigurationSection(key);
        BoxSound sound = BoxSound.from(s, defSound, defVol, defPitch);
        checkSoundName("audio." + key + ".sound", sound.sound());
        out.put(key, sound);
    }

    /**
     * Validates a {@link org.bukkit.Sound} constant name at load time without initializing the
     * {@code Sound} class. {@code Sound} is a registry-backed interface in this API version; its
     * class initializer walks {@code Registry.SOUNDS} and throws when no server is running, so
     * {@link BoxSound#resolve()} (which reads a constant) is safe only at play time. A reflective
     * {@code getField} lookup checks the name against the class structure without triggering that
     * initializer, so a bad name fails on load here and on any machine, server or not.
     */
    private static void checkSoundName(String key, String name) {
        try {
            org.bukkit.Sound.class.getField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(
                    "Invalid config value for '" + key + "': " + name + " (unknown sound)");
        }
    }

    /**
     * Reads the growth {@code stages.N} sections in file order and validates that thresholds
     * strictly ascend. When the section is absent the shipped three-stage table is used.
     */
    private static List<StageDef> readStages(ConfigurationSection root) {
        ConfigurationSection section = root.getConfigurationSection("stages");
        List<StageDef> stages = new ArrayList<>();
        if (section == null) {
            stages.add(new StageDef(1, 0L, 30.0, 20, 6.0, false));
            stages.add(new StageDef(2, 400L, 60.0, 14, 8.0, false));
            stages.add(new StageDef(3, 1600L, 100.0, 8, 10.0, true));
            return stages;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            check(s != null, "stages." + key, section.get(key), "must be a section");
            int index;
            try {
                index = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid config value for 'stages." + key + "': " + key
                                + " (must be an integer stage number)");
            }
            long xpThreshold = s.getLong("xp-threshold", 0L);
            double maxHealth = s.getDouble("max-health", 20.0);
            int stepIntervalTicks = s.getInt("step-interval-ticks", 20);
            double feedRadius = s.getDouble("feed-radius", 6.0);
            boolean killsOnContact = s.getBoolean("kills-on-contact", false);

            String prefix = "stages." + key + ".";
            check(xpThreshold >= 0, prefix + "xp-threshold", xpThreshold, "must be >= 0");
            check(maxHealth > 0, prefix + "max-health", maxHealth, "must be > 0");
            check(stepIntervalTicks >= 1, prefix + "step-interval-ticks", stepIntervalTicks,
                    "must be >= 1");
            check(feedRadius >= 0, prefix + "feed-radius", feedRadius, "must be >= 0");
            stages.add(new StageDef(index, xpThreshold, maxHealth, stepIntervalTicks, feedRadius,
                    killsOnContact));
        }
        check(!stages.isEmpty(), "stages", "(empty)", "must define at least one stage");
        for (int i = 1; i < stages.size(); i++) {
            long prev = stages.get(i - 1).xpThreshold();
            long cur = stages.get(i).xpThreshold();
            check(cur > prev, "stages." + stages.get(i).index() + ".xp-threshold", cur,
                    "must be greater than the previous stage threshold (" + prev + ")");
        }
        return stages;
    }

    private static Material readMaterial(ConfigurationSection root, String key, String def) {
        String name = root.getString(key, def);
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid config value for '" + key + "': " + name + " (unknown material)");
        }
    }

    private static void check(boolean valid, String key, Object value, String requirement) {
        if (!valid) {
            throw new IllegalArgumentException(
                    "Invalid config value for '" + key + "': " + value + " (" + requirement + ")");
        }
    }
}

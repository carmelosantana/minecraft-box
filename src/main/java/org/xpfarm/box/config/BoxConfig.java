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
     * A parsed configuration paired with the warnings produced while substituting the shipped
     * default for any invalid scalar / unknown sound / unknown material. The caller (the plugin)
     * logs each warning and proceeds with {@link #config()} — a single bad value never disables the
     * plugin (acceptance check 19). {@link BoxConfig#from(ConfigurationSection)} discards them.
     *
     * @param config the validated configuration, with every invalid value defaulted
     * @param warnings one message per substituted key, in read order; empty when the file was clean
     */
    public record Result(BoxConfig config, List<String> warnings) {
        public Result {
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * Reads and validates a configuration section (typically the plugin's
     * {@code FileConfiguration}), substituting the shipped default for any out-of-range scalar (or
     * unknown sound / material name) rather than throwing, and recording a human-readable warning
     * naming the offending key, its value, and the substitution. Missing keys silently fall back to
     * their defaults. Only a structurally broken file — a malformed {@code stages} section — still
     * throws, so the caller can disable gracefully rather than run a half-wired creature. This is the
     * per-key fall-back that acceptance check 19 requires: one typo'd number never takes the plugin
     * offline.
     *
     * @param root the configuration root to read from
     * @return the validated config paired with the list of substitution warnings (possibly empty)
     * @throws IllegalArgumentException if the {@code stages} section is structurally invalid
     */
    public static Result fromValidated(ConfigurationSection root) {
        List<String> warnings = new ArrayList<>();

        boolean spawnEnabled = root.getBoolean("spawn.enabled", false);
        int checkIntervalSeconds = defInt(root.getInt("spawn.check-interval-seconds", 300), 300,
                v -> v >= 0, "spawn.check-interval-seconds", "must be >= 0", warnings);
        double chance = defDouble(root.getDouble("spawn.chance", 0.08), 0.08,
                v -> v >= 0.0 && v <= 1.0, "spawn.chance", "must be 0.0 - 1.0", warnings);
        int minDistance = defInt(root.getInt("spawn.min-distance", 24), 24, v -> v >= 0,
                "spawn.min-distance", "must be >= 0", warnings);
        int maxDistance = defInt(root.getInt("spawn.max-distance", 48), 48, v -> v >= 0,
                "spawn.max-distance", "must be >= 0", warnings);
        // Cross-constraint: if the band is inverted, reset the whole band to its defaults.
        if (minDistance > maxDistance) {
            warnings.add(warnMessage("spawn.min-distance/spawn.max-distance",
                    minDistance + " > " + maxDistance, "min must be <= max", "24 / 48"));
            minDistance = 24;
            maxDistance = 48;
        }
        int perPlayerCap = defInt(root.getInt("spawn.per-player-cap", 1), 1, v -> v >= 1,
                "spawn.per-player-cap", "must be >= 1", warnings);
        int serverCap = defInt(root.getInt("spawn.server-cap", 4), 4, v -> v >= 0,
                "spawn.server-cap", "must be >= 0", warnings);
        boolean requireSkyAccess = root.getBoolean("spawn.require-sky-access", true);
        int nightStart = defInt(root.getInt("spawn.night-start", 13000), 13000, v -> v >= 0,
                "spawn.night-start", "must be >= 0", warnings);
        int nightEnd = root.getInt("spawn.night-end", 23000);
        // Cross-constraint: if the window is inverted, reset the whole window to its defaults.
        if (nightStart >= nightEnd) {
            warnings.add(warnMessage("spawn.night-start/spawn.night-end",
                    nightStart + " >= " + nightEnd, "start must be < end", "13000 / 23000"));
            nightStart = 13000;
            nightEnd = 23000;
        }
        int minDistanceFromWorldSpawn = defInt(root.getInt("spawn.min-distance-from-world-spawn",
                128), 128, v -> v >= 0, "spawn.min-distance-from-world-spawn", "must be >= 0",
                warnings);

        double fovCosine = defDouble(root.getDouble("gaze.fov-cosine", 0.6), 0.6,
                v -> v >= -1.0 && v <= 1.0, "gaze.fov-cosine", "must be -1.0 - 1.0", warnings);
        int gazeMaxDistance = defInt(root.getInt("gaze.max-distance", 48), 48, v -> v >= 0,
                "gaze.max-distance", "must be >= 0", warnings);
        int lockOnTicks = defInt(root.getInt("gaze.lock-on-ticks", 10), 10, v -> v >= 0,
                "gaze.lock-on-ticks", "must be >= 0", warnings);
        boolean ignoreCreative = root.getBoolean("gaze.ignore-creative", true);
        boolean ignoreSpectator = root.getBoolean("gaze.ignore-spectator", true);

        int maxStepUp = defInt(root.getInt("movement.max-step-up", 1), 1, v -> v >= 0,
                "movement.max-step-up", "must be >= 0", warnings);
        int maxStepDown = defInt(root.getInt("movement.max-step-down", 3), 3, v -> v >= 0,
                "movement.max-step-down", "must be >= 0", warnings);
        int maxClimbHeight = defInt(root.getInt("movement.max-climb-height", 24), 24, v -> v >= 0,
                "movement.max-climb-height", "must be >= 0", warnings);
        boolean allowCeilingTraversal = root.getBoolean("movement.allow-ceiling-traversal", true);

        int xpPerSecond = defInt(root.getInt("feeding.xp-per-second", 8), 8, v -> v >= 0,
                "feeding.xp-per-second", "must be >= 0", warnings);
        boolean requireXpToOpen = root.getBoolean("feeding.require-xp-to-open", true);

        boolean starvationEnabled = root.getBoolean("starvation.enabled", true);
        int onsetSeconds = defInt(root.getInt("starvation.onset-seconds", 300), 300, v -> v >= 0,
                "starvation.onset-seconds", "must be >= 0", warnings);
        int maxSeconds = defInt(root.getInt("starvation.max-seconds", 1800), 1800, v -> v >= 0,
                "starvation.max-seconds", "must be >= 0", warnings);
        double stepIntervalMultiplier = defDouble(
                root.getDouble("starvation.step-interval-multiplier", 0.5), 0.5, v -> v >= 0,
                "starvation.step-interval-multiplier", "must be >= 0", warnings);
        double volumeMultiplier = defDouble(root.getDouble("starvation.volume-multiplier", 1.5),
                1.5, v -> v >= 0, "starvation.volume-multiplier", "must be >= 0", warnings);

        boolean disorientationEnabled = root.getBoolean("disorientation.enabled", true);
        int nauseaTicks = defInt(root.getInt("disorientation.nausea-ticks", 120), 120, v -> v >= 0,
                "disorientation.nausea-ticks", "must be >= 0", warnings);
        int darknessTicks = defInt(root.getInt("disorientation.darkness-ticks", 100), 100,
                v -> v >= 0, "disorientation.darkness-ticks", "must be >= 0", warnings);

        double contactRadius = defDouble(root.getDouble("contact.radius", 1.5), 1.5, v -> v >= 0,
                "contact.radius", "must be >= 0", warnings);
        int contactBlindnessTicks = defInt(root.getInt("contact.blindness-ticks", 200), 200,
                v -> v >= 0, "contact.blindness-ticks", "must be >= 0", warnings);
        int contactNauseaTicks = defInt(root.getInt("contact.nausea-ticks", 300), 300, v -> v >= 0,
                "contact.nausea-ticks", "must be >= 0", warnings);

        Material artifactMaterial = readMaterial(root, "artifact.material", "ECHO_SHARD", warnings);
        String artifactName = root.getString("artifact.display-name", "Cursed Artifact");
        double xpReturnRatio = defDouble(root.getDouble("artifact.xp-return-ratio", 0.75), 0.75,
                v -> v >= 0.0 && v <= 1.0, "artifact.xp-return-ratio", "must be 0.0 - 1.0", warnings);
        boolean curseIntegration = root.getBoolean("artifact.curse-integration", true);

        int offlineDormantMinutes = defInt(root.getInt("lifetime.offline-dormant-minutes", 30), 30,
                v -> v >= 0, "lifetime.offline-dormant-minutes", "must be >= 0", warnings);
        int maxLifetimeHours = defInt(root.getInt("lifetime.max-lifetime-hours", 0), 0, v -> v >= 0,
                "lifetime.max-lifetime-hours", "must be >= 0", warnings);
        boolean unbindOnVictimDeath = root.getBoolean("lifetime.unbind-on-victim-death", true);

        Map<String, BoxSound> sounds = readSounds(root, warnings);
        List<StageDef> stages = readStages(root);

        BoxConfig config = new BoxConfig(spawnEnabled, checkIntervalSeconds, chance, minDistance,
                maxDistance, perPlayerCap, serverCap, requireSkyAccess, nightStart, nightEnd,
                minDistanceFromWorldSpawn, fovCosine, gazeMaxDistance, lockOnTicks, ignoreCreative,
                ignoreSpectator, maxStepUp, maxStepDown, maxClimbHeight, allowCeilingTraversal,
                xpPerSecond, requireXpToOpen, stages, starvationEnabled, onsetSeconds, maxSeconds,
                stepIntervalMultiplier, volumeMultiplier, disorientationEnabled, nauseaTicks,
                darknessTicks, contactRadius, contactBlindnessTicks, contactNauseaTicks, sounds,
                artifactMaterial, artifactName, xpReturnRatio, curseIntegration,
                offlineDormantMinutes, maxLifetimeHours, unbindOnVictimDeath);
        return new Result(config, warnings);
    }

    /**
     * Convenience over {@link #fromValidated(ConfigurationSection)} that discards the warning list
     * and returns just the (default-substituted) configuration. Retained for callers and tests that
     * only need a config; the plugin uses {@link #fromValidated(ConfigurationSection)} so it can log
     * each substitution.
     *
     * @param root the configuration root to read from
     * @return the validated, immutable configuration with every invalid value defaulted
     * @throws IllegalArgumentException if the {@code stages} section is structurally invalid
     */
    public static BoxConfig from(ConfigurationSection root) {
        return fromValidated(root).config();
    }

    /**
     * Returns {@code value} when {@code valid}, otherwise records a warning naming the key, the bad
     * value, and the substitution, and returns {@code fallback}.
     */
    private static int defInt(int value, int fallback, java.util.function.IntPredicate valid,
            String key, String requirement, List<String> warnings) {
        if (valid.test(value)) {
            return value;
        }
        warnings.add(warnMessage(key, value, requirement, fallback));
        return fallback;
    }

    /** The {@code double} counterpart to {@link #defInt}. */
    private static double defDouble(double value, double fallback,
            java.util.function.DoublePredicate valid, String key, String requirement,
            List<String> warnings) {
        if (valid.test(value)) {
            return value;
        }
        warnings.add(warnMessage(key, value, requirement, fallback));
        return fallback;
    }

    /** The single warning format: names the key, the rejected value, the rule, and the default. */
    private static String warnMessage(String key, Object value, String requirement,
            Object fallback) {
        return "Invalid config value for '" + key + "': " + value + " (" + requirement
                + "); falling back to default " + fallback;
    }

    /**
     * Reads the {@code audio.*} events, one {@link BoxSound} per key with its shipped default. An
     * unknown sound name is defaulted to that key's shipped sound (keeping any configured volume /
     * pitch) and a warning recorded, so a typo'd sound degrades to the vanilla default rather than
     * failing startup (acceptance check 19).
     */
    private static Map<String, BoxSound> readSounds(ConfigurationSection root,
            List<String> warnings) {
        ConfigurationSection audio = root.getConfigurationSection("audio");
        Map<String, BoxSound> sounds = new LinkedHashMap<>();
        putSound(sounds, audio, "dormant-ambience", "BLOCK_SCULK_SENSOR_CLICKING", 0.6f, 0.5f,
                warnings);
        putSound(sounds, audio, "lock-on-sting", "BLOCK_SCULK_SHRIEKER_SHRIEK", 1.0f, 0.7f,
                warnings);
        putSound(sounds, audio, "proximity-pulse", "ENTITY_WARDEN_HEARTBEAT", 0.8f, 0.8f, warnings);
        putSound(sounds, audio, "movement", "BLOCK_SCULK_SPREAD", 0.3f, 0.5f, warnings);
        putSound(sounds, audio, "feeding", "ENTITY_EXPERIENCE_ORB_PICKUP", 0.7f, 0.5f, warnings);
        putSound(sounds, audio, "opening", "ENTITY_SHULKER_OPEN", 0.9f, 0.4f, warnings);
        putSound(sounds, audio, "death", "ENTITY_WARDEN_DEATH", 1.0f, 0.6f, warnings);
        putSound(sounds, audio, "haunting", "MUSIC_DISC_11", 0.5f, 1.0f, warnings);
        return sounds;
    }

    private static void putSound(Map<String, BoxSound> out, ConfigurationSection audio, String key,
            String defSound, float defVol, float defPitch, List<String> warnings) {
        ConfigurationSection s = audio == null ? null : audio.getConfigurationSection(key);
        BoxSound sound = BoxSound.from(s, defSound, defVol, defPitch);
        if (!isKnownSoundName(sound.sound())) {
            warnings.add(warnMessage("audio." + key + ".sound", sound.sound(), "unknown sound",
                    defSound));
            // Keep the operator's volume / pitch / interval; only the bad sound name is replaced.
            sound = new BoxSound(defSound, sound.volume(), sound.pitch(), sound.intervalSeconds());
        }
        out.put(key, sound);
    }

    /**
     * Whether {@code name} is a real {@link org.bukkit.Sound} constant, checked at load time without
     * initializing the {@code Sound} class. {@code Sound} is a registry-backed interface in this API
     * version; its class initializer walks {@code Registry.SOUNDS} and throws when no server is
     * running, so {@link BoxSound#resolve()} (which reads a constant) is safe only at play time. A
     * reflective {@code getField} lookup checks the name against the class structure without
     * triggering that initializer, so the name is verifiable here on any machine, server or not.
     */
    private static boolean isKnownSoundName(String name) {
        try {
            org.bukkit.Sound.class.getField(name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
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

    /**
     * Resolves a {@link Material} name, defaulting an unknown name to {@code def} (a known-valid
     * constant) with a recorded warning rather than throwing, so a typo'd material degrades safely
     * (acceptance check 19).
     */
    private static Material readMaterial(ConfigurationSection root, String key, String def,
            List<String> warnings) {
        String name = root.getString(key, def);
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            warnings.add(warnMessage(key, name, "unknown material", def));
            return Material.valueOf(def);
        }
    }

    private static void check(boolean valid, String key, Object value, String requirement) {
        if (!valid) {
            throw new IllegalArgumentException(
                    "Invalid config value for '" + key + "': " + value + " (" + requirement + ")");
        }
    }
}

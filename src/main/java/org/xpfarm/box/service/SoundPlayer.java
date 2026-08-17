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
package org.xpfarm.box.service;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.xpfarm.box.config.BoxConfig;
import org.xpfarm.box.config.BoxSound;

/**
 * Bukkit adapter that plays the creature's configured vanilla sounds by their {@code audio.*}
 * config key.
 *
 * <p>Each key is looked up in {@link BoxConfig#sounds()} and resolved to its {@link org.bukkit.Sound}
 * constant via {@link BoxSound#resolve()} at play time (the config already validated every shipped
 * key's name at load, so resolution here does not throw for configured keys). An <em>unknown</em>
 * key — one never defined in the audio map — is a no-op: nothing is played, and the key is logged
 * once at {@code WARNING} so a caller typo surfaces without spamming the log every tick.
 *
 * <p>Both entry points are server-side {@code playSound} calls, which Geyser translates for Bedrock
 * clients, so playback is Bedrock-safe.
 *
 * <h2>Gate-7a obligations (live-only, not unit-tested here)</h2>
 *
 * Actual audible playback and the {@link org.bukkit.Sound} resolution of every configured key are
 * verified against a running server; the only pure seam here — no-op-plus-log-once on an unknown
 * key — is exercised by {@code SoundPlayer}'s own guard set and does not need a live server.
 */
public final class SoundPlayer {

    private static final Logger LOG = Logger.getLogger(SoundPlayer.class.getName());

    private final BoxConfig config;

    /**
     * Keys already warned about, so an unknown key logs exactly once no matter how often the tick
     * loop asks for it. Concurrent-safe in case sounds are ever triggered off the main thread.
     */
    private final Set<String> warnedKeys = ConcurrentHashMap.newKeySet();

    /**
     * @param config the validated configuration whose {@code audio.*} map backs every lookup
     */
    public SoundPlayer(BoxConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Plays the sound bound to {@code key} at a world location, audible to everyone in range.
     * A {@code null} location (or one with no world) and an unknown key are both no-ops.
     *
     * @param at the world location to play at
     * @param key the {@code audio.*} config key (e.g. {@code "feeding"})
     */
    public void play(Location at, String key) {
        if (at == null) {
            return;
        }
        BoxSound sound = resolveOrWarn(key);
        if (sound == null) {
            return;
        }
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(at, sound.resolve(), sound.volume(), sound.pitch());
    }

    /**
     * Plays the sound bound to {@code key} to a single player, at that player's own location.
     * A {@code null} player and an unknown key are both no-ops.
     *
     * @param player the player to play the sound to
     * @param key the {@code audio.*} config key (e.g. {@code "feeding"})
     */
    public void playTo(Player player, String key) {
        if (player == null) {
            return;
        }
        BoxSound sound = resolveOrWarn(key);
        if (sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound.resolve(), sound.volume(), sound.pitch());
    }

    /**
     * Looks up the {@link BoxSound} for a key, returning {@code null} (and logging once) when the
     * key is not present in the configured audio map.
     */
    private BoxSound resolveOrWarn(String key) {
        BoxSound sound = key == null ? null : config.sounds().get(key);
        if (sound == null && warnedKeys.add(String.valueOf(key))) {
            LOG.log(Level.WARNING, "No configured Box sound for key ''{0}''; ignoring.", key);
        }
        return sound;
    }
}

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

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Immutable description of one vanilla sound event the creature plays.
 *
 * <p>Pure data: no server calls until {@link #resolve()} is invoked. The stored
 * {@code sound} is the {@link Sound} enum constant name exactly as written in
 * {@code config.yml}; it is not validated until {@link #resolve()} so this record can be
 * built off the main thread and off a running server.
 *
 * @param sound vanilla {@link Sound} enum constant name
 * @param volume playback volume
 * @param pitch playback pitch
 * @param intervalSeconds seconds between repeats for periodic sounds; {@code 0} disables
 */
public record BoxSound(String sound, float volume, float pitch, int intervalSeconds) {

    /**
     * Reads one {@code audio.*} section, falling back to the supplied defaults for any
     * missing key. {@code interval-seconds} defaults to {@code 0} (non-periodic).
     *
     * @param s the audio subsection, or {@code null} to take every default
     * @param defSound default {@link Sound} constant name
     * @param defVol default volume
     * @param defPitch default pitch
     * @return the sound description read from {@code s}
     */
    public static BoxSound from(ConfigurationSection s, String defSound, float defVol,
            float defPitch) {
        if (s == null) {
            return new BoxSound(defSound, defVol, defPitch, 0);
        }
        String sound = s.getString("sound", defSound);
        float volume = (float) s.getDouble("volume", defVol);
        float pitch = (float) s.getDouble("pitch", defPitch);
        int intervalSeconds = s.getInt("interval-seconds", 0);
        return new BoxSound(sound, volume, pitch, intervalSeconds);
    }

    /**
     * Resolves the stored name to its {@link Sound} constant.
     *
     * @return the matching {@link Sound}
     * @throws IllegalArgumentException if {@code sound} is not a known {@link Sound} constant
     */
    public Sound resolve() {
        try {
            return Sound.valueOf(sound);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown sound: " + sound);
        }
    }
}

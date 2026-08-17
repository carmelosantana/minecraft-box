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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BoxConfigTest {

    /** The shipped config.yml, loaded exactly as Paper would. */
    private static BoxConfig shipped() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(
                        BoxConfigTest.class.getResourceAsStream("/config.yml")));
        return BoxConfig.from(yaml);
    }

    @Test
    void shippedConfigDisablesSpawningByDefault() {
        assertFalse(shipped().spawnEnabled(), "spawn.enabled must ship false");
    }

    @Test
    void shippedConfigHasThreeStagesTopKillsOnContact() {
        BoxConfig c = shipped();
        assertEquals(3, c.stages().size());
        assertTrue(c.stages().get(2).killsOnContact(), "stage 3 must kill on contact");
        assertFalse(c.stages().get(0).killsOnContact(), "stage 1 must not kill on contact");
    }

    @Test
    void fovCosineOutOfRangeThrowsNamingTheKey() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("gaze.fov-cosine", 4.0); // valid range -1..1
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> BoxConfig.from(y));
        assertTrue(ex.getMessage().contains("gaze.fov-cosine"), ex.getMessage());
    }

    @Test
    void unknownSoundNameThrows() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("audio.death.sound", "NOT_A_SOUND");
        assertThrows(IllegalArgumentException.class, () -> BoxConfig.from(y));
    }
}

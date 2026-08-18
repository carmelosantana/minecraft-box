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
    void shippedConfigProducesNoWarnings() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(
                        BoxConfigTest.class.getResourceAsStream("/config.yml")));
        assertTrue(BoxConfig.fromValidated(yaml).warnings().isEmpty(),
                "the shipped config.yml must load with zero substitution warnings");
    }

    @Test
    void fovCosineOutOfRangeFallsBackToDefaultAndWarns() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("gaze.fov-cosine", 4.0); // outside the valid -1..1 range
        BoxConfig.Result result = BoxConfig.fromValidated(y);
        assertEquals(0.6, result.config().fovCosine(), 1.0e-9,
                "an out-of-range fov-cosine must fall back to the shipped default, not throw");
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("gaze.fov-cosine")),
                "the warning must name the offending key: " + result.warnings());
    }

    @Test
    void unknownSoundNameFallsBackToDefaultAndWarns() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("audio.death.sound", "NOT_A_SOUND");
        BoxConfig.Result result = BoxConfig.fromValidated(y);
        assertEquals("ENTITY_WARDEN_DEATH", result.config().sounds().get("death").sound(),
                "an unknown sound name must fall back to that key's shipped default");
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("audio.death.sound")),
                "the warning must name the offending sound key: " + result.warnings());
    }

    @Test
    void unknownSoundKeepsConfiguredVolumeAndPitch() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("audio.death.sound", "NOT_A_SOUND");
        y.set("audio.death.volume", 0.25);
        y.set("audio.death.pitch", 1.75);
        BoxSound death = BoxConfig.fromValidated(y).config().sounds().get("death");
        assertEquals(0.25f, death.volume(), 1.0e-6f, "operator volume must survive the substitution");
        assertEquals(1.75f, death.pitch(), 1.0e-6f, "operator pitch must survive the substitution");
    }

    @Test
    void invalidBandFallsBackWithoutFailing() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("spawn.min-distance", 80);
        y.set("spawn.max-distance", 10); // inverted band
        BoxConfig c = BoxConfig.fromValidated(y).config();
        assertTrue(c.minDistance() <= c.maxDistance(),
                "an inverted distance band must be reset to a consistent default pair");
    }

    @Test
    void structurallyBrokenStagesStillThrows() {
        YamlConfiguration y = new YamlConfiguration();
        // A stage key that is not an integer is a structural error, not a scalar-range one.
        y.set("stages.notanumber.xp-threshold", 0);
        assertThrows(IllegalArgumentException.class, () -> BoxConfig.fromValidated(y),
                "a malformed stages section is structural and must still disable gracefully");
    }
}

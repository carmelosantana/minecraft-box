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
package org.xpfarm.box;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point.
 *
 * <p>Scaffold only. Gate 4 ({@code minecraft-plugin-dev}) implements the creature itself —
 * gaze detection, unobserved movement, feeding, growth staging, and the cursed artifact.
 * The design this scaffold is built against lives at
 * {@code docs/superpowers/specs/2026-08-17-box-design.md}; the pipeline's source of truth is
 * {@code docs/PLUGIN_CHECKLIST.md}.
 */
public final class BoxPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("The Box enabled (scaffold — creature behavior not yet implemented)");
    }

    @Override
    public void onDisable() {
        getLogger().info("The Box disabled");
    }
}

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

/**
 * Immutable definition of one growth stage, read from a {@code stages.N} section.
 *
 * <p>Minimal on purpose: Task 2 builds {@code StageTable} and the starvation curve around
 * this record. It carries only the per-stage values {@code config.yml} defines.
 *
 * @param index one-based stage number as written in {@code config.yml}
 * @param xpThreshold cumulative banked experience points to reach this stage
 * @param maxHealth the creature's maximum health at this stage
 * @param stepIntervalTicks ticks between unobserved movement steps at this stage
 * @param feedRadius blocks within which the creature feeds at this stage
 * @param killsOnContact whether reaching a victim at this stage is lethal
 */
public record StageDef(
        int index,
        long xpThreshold,
        double maxHealth,
        int stepIntervalTicks,
        double feedRadius,
        boolean killsOnContact) {
}

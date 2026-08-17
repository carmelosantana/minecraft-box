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
package org.xpfarm.box.model;

import java.util.List;
import org.xpfarm.box.config.StageDef;

/**
 * Pure growth-stage lookup over an ascending-threshold table of {@link StageDef}s.
 *
 * <p>The stages must be in ascending {@code xpThreshold} order, which {@code BoxConfig}
 * already validates at load, so this class does not re-sort. No Bukkit is involved.
 */
public final class StageTable {

    private final List<StageDef> stages;

    /**
     * @param stages growth stages in ascending threshold order; must be non-empty and its first
     *     stage must have a threshold of {@code 0} so every banked value maps to a stage
     * @throws IllegalArgumentException if {@code stages} is empty
     */
    public StageTable(List<StageDef> stages) {
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("stages must not be empty");
        }
        this.stages = List.copyOf(stages);
    }

    /**
     * Returns the highest stage whose {@code xpThreshold} is {@code <= bankedXp}. Values below
     * the first threshold return the first stage.
     *
     * @param bankedXp banked experience points (not levels), may be any value
     * @return the matching stage
     */
    public StageDef stageFor(long bankedXp) {
        StageDef current = stages.get(0);
        for (StageDef stage : stages) {
            if (stage.xpThreshold() <= bankedXp) {
                current = stage;
            } else {
                break;
            }
        }
        return current;
    }

    /** @return the number of stages in the table */
    public int count() {
        return stages.size();
    }
}

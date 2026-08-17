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

/**
 * A probe over the world grid that reports whether a single creature block may occupy a cell.
 *
 * <p>This is the sole world dependency of {@link StepPlanner}. Keeping it a bare functional
 * interface over integer coordinates means the planner is pure logic: unit tests supply an
 * in-memory grid, while the live plugin (a later task) supplies a Bukkit-backed adapter. No Bukkit
 * type appears here.
 */
@FunctionalInterface
public interface Passability {

    /**
     * Reports whether a creature block can stand in the cell at {@code (x, y, z)}.
     *
     * @param x the cell's x coordinate
     * @param y the cell's y coordinate
     * @param z the cell's z coordinate
     * @return {@code true} when the cell is air or otherwise non-solid (occupiable); {@code false}
     *     when a solid block fills it
     */
    boolean isPassable(int x, int y, int z);
}

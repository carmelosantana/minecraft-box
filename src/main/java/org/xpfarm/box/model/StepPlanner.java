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

import java.util.Optional;

/**
 * Pure single-step movement planner for the creature.
 *
 * <p>Given the creature's current cell, the victim's cell, and a {@link Passability} probe over the
 * world, {@link #next} decides the one adjacent cell the creature should move into this tick to
 * close on the victim. The planner is <strong>stateless</strong>: a multi-tick climb is not
 * remembered, it is re-derived from the world every call. This class has no Bukkit dependency and is
 * exercised entirely against in-memory grids.
 *
 * <h2>Movement model</h2>
 *
 * <p>The creature moves one cell along the dominant horizontal axis toward the victim. At that
 * forward column the planner tries, in order:
 *
 * <ol>
 *   <li><b>Flat ground step</b> — enter the forward cell at the same height when it is passable and
 *       has solid floor support beneath it.
 *   <li><b>Step up</b> — rise within the creature's own column (needing clear headroom) and land on
 *       a ledge up to {@code maxStepUp} higher.
 *   <li><b>Step down</b> — walk off the forward edge and drop to the first supported landing up to
 *       {@code maxStepDown} lower.
 *   <li><b>Wall climb</b> — when a solid wall face blocks the forward step and the wall has a top
 *       reachable within {@code maxClimbHeight}, ascend one cell straight up along the near face.
 *       The <em>first</em> move of a climb is upward ({@code from.y + 1}), never into the wall.
 *   <li><b>Ceiling traversal</b> — when {@code allowCeiling} and the creature clings under a solid
 *       ceiling that continues over the forward cell, shuffle forward while hanging (no floor
 *       needed).
 *   <li><b>Flank</b> — step sideways along the perpendicular axis to begin going around the
 *       obstacle.
 *   <li><b>Wait</b> — {@link Optional#empty()} when every candidate above is non-passable.
 * </ol>
 *
 * <p>Every returned cell is guaranteed passable and never equal to {@code from}.
 */
public final class StepPlanner {

    /**
     * A single integer lattice cell in the world grid.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     */
    public record Cell(int x, int y, int z) {
    }

    private final int maxStepUp;
    private final int maxStepDown;
    private final int maxClimbHeight;
    private final boolean allowCeiling;

    /**
     * Creates a planner with the given movement tolerances.
     *
     * @param maxStepUp the greatest height, in cells, the creature can step up onto a ledge
     * @param maxStepDown the greatest height, in cells, the creature can drop down to a landing
     * @param maxClimbHeight the tallest wall, in cells, the creature is willing to climb
     * @param allowCeiling whether the creature can cling to and traverse ceilings
     */
    public StepPlanner(int maxStepUp, int maxStepDown, int maxClimbHeight, boolean allowCeiling) {
        this.maxStepUp = maxStepUp;
        this.maxStepDown = maxStepDown;
        this.maxClimbHeight = maxClimbHeight;
        this.allowCeiling = allowCeiling;
    }

    /**
     * Chooses the single best cell for the creature to move into this tick.
     *
     * @param from the creature's current cell
     * @param victim the cell the creature is closing on
     * @param world the passability probe over the world grid
     * @return the next cell (always passable, never {@code from}), or {@link Optional#empty()} when
     *     the creature is sealed in and must wait
     */
    public Optional<Cell> next(Cell from, Cell victim, Passability world) {
        int dx = victim.x() - from.x();
        int dz = victim.z() - from.z();

        // Victim shares the creature's horizontal column: the only progress is vertical.
        if (dx == 0 && dz == 0) {
            return verticalToward(from, victim, world);
        }

        // Dominant horizontal axis: exactly one of sx, sz is non-zero.
        int sx = 0;
        int sz = 0;
        if (Math.abs(dx) >= Math.abs(dz)) {
            if (dx != 0) {
                sx = Integer.signum(dx);
            } else {
                sz = Integer.signum(dz);
            }
        } else {
            sz = Integer.signum(dz);
        }

        int x = from.x();
        int y = from.y();
        int z = from.z();
        int nx = x + sx;
        int nz = z + sz;

        // 1. Flat ground step: forward cell passable with solid floor support.
        if (passable(world, nx, y, nz) && solid(world, nx, y - 1, nz)) {
            return Optional.of(new Cell(nx, y, nz));
        }

        // 2. Step up: rise within our own column (clear headroom), then onto a higher ledge.
        for (int up = 1; up <= maxStepUp; up++) {
            int h = y + up;
            if (!passable(world, x, h, z)) {
                break; // headroom above us is blocked; cannot rise any further
            }
            if (passable(world, nx, h, nz) && solid(world, nx, h - 1, nz)) {
                return Optional.of(new Cell(nx, h, nz));
            }
        }

        // 3. Step down: walk off the forward edge and drop to the first supported landing.
        if (passable(world, nx, y, nz)) {
            for (int h = y - 1; h >= y - maxStepDown; h--) {
                if (!passable(world, nx, h + 1, nz)) {
                    break; // the drop is obstructed partway down
                }
                if (passable(world, nx, h, nz) && solid(world, nx, h - 1, nz)) {
                    return Optional.of(new Cell(nx, h, nz));
                }
            }
        }

        // 4. Wall climb: forward blocked by a solid face and a top is reachable in budget.
        if (solid(world, nx, y, nz) && passable(world, x, y + 1, z) && wallTopReachable(world, nx, y, nz)) {
            return Optional.of(new Cell(x, y + 1, z));
        }

        // 5. Ceiling traversal: cling under a continuous ceiling and shuffle forward.
        if (allowCeiling
                && solid(world, x, y + 1, z)
                && solid(world, nx, y + 1, nz)
                && passable(world, nx, y, nz)) {
            return Optional.of(new Cell(nx, y, nz));
        }

        // 6. Flank: step sideways along the perpendicular axis to begin going around.
        int perpX = sx != 0 ? 0 : 1;
        int perpZ = sx != 0 ? 1 : 0;
        int secondary = sx != 0 ? dz : dx;
        int firstDir = secondary != 0 ? Integer.signum(secondary) : 1;
        for (int dir : new int[] {firstDir, -firstDir}) {
            int fx = x + perpX * dir;
            int fz = z + perpZ * dir;
            if (passable(world, fx, y, fz) && solid(world, fx, y - 1, fz)) {
                return Optional.of(new Cell(fx, y, fz));
            }
        }

        // 7. Sealed in: wait.
        return Optional.empty();
    }

    /** Moves one cell vertically toward a victim that shares the creature's horizontal column. */
    private Optional<Cell> verticalToward(Cell from, Cell victim, Passability world) {
        int x = from.x();
        int y = from.y();
        int z = from.z();
        if (victim.y() > y && passable(world, x, y + 1, z)) {
            return Optional.of(new Cell(x, y + 1, z));
        }
        if (victim.y() < y && passable(world, x, y - 1, z) && solid(world, x, y - 2, z)) {
            return Optional.of(new Cell(x, y - 1, z));
        }
        return Optional.empty();
    }

    /**
     * Reports whether the wall ahead has a top the creature could eventually step over within
     * {@code maxClimbHeight} cells above its current height. A wall taller than the budget is not
     * worth starting to climb.
     */
    private boolean wallTopReachable(Passability world, int nx, int y, int nz) {
        for (int k = 1; k <= maxClimbHeight; k++) {
            if (passable(world, nx, y + k, nz)) {
                return true;
            }
        }
        return false;
    }

    private static boolean passable(Passability world, int x, int y, int z) {
        return world.isPassable(x, y, z);
    }

    private static boolean solid(Passability world, int x, int y, int z) {
        return !world.isPassable(x, y, z);
    }
}

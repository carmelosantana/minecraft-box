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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StepPlannerTest {
    /** Solid floor at y=0 across a wide area; everything else air. */
    private static Passability floorWorld(Set<StepPlanner.Cell> extraSolids) {
        return (x, y, z) -> y > 0 && !extraSolids.contains(new StepPlanner.Cell(x, y, z));
    }

    /** A world with no implicit floor: passable everywhere except the given solid cells. */
    private static Passability gridWorld(Set<StepPlanner.Cell> solids) {
        return (x, y, z) -> !solids.contains(new StepPlanner.Cell(x, y, z));
    }

    private final StepPlanner planner = new StepPlanner(1, 3, 24, true);

    @Test void stepsTowardVictimOnFlatGround() {
        Optional<StepPlanner.Cell> n = planner.next(
                new StepPlanner.Cell(0, 1, 0), new StepPlanner.Cell(5, 1, 0), floorWorld(Set.of()));
        assertEquals(Optional.of(new StepPlanner.Cell(1, 1, 0)), n);
    }

    @Test void climbsAWallTooTallToStepOver() {
        // wall of solids at x=1, y=1..5
        Set<StepPlanner.Cell> wall = new HashSet<>();
        for (int y = 1; y <= 5; y++) wall.add(new StepPlanner.Cell(1, y, 0));
        Optional<StepPlanner.Cell> n = planner.next(
                new StepPlanner.Cell(0, 1, 0), new StepPlanner.Cell(5, 1, 0), floorWorld(wall));
        assertTrue(n.isPresent());
        // first move of a climb is upward along the wall face, not into the wall
        assertEquals(new StepPlanner.Cell(0, 2, 0), n.get());
    }

    @Test void fullyWalledInWaits() {
        Set<StepPlanner.Cell> box = new HashSet<>();
        box.add(new StepPlanner.Cell(1, 1, 0));
        box.add(new StepPlanner.Cell(-1, 1, 0));
        box.add(new StepPlanner.Cell(0, 1, 1));
        box.add(new StepPlanner.Cell(0, 1, -1));
        box.add(new StepPlanner.Cell(0, 2, 0)); // ceiling too
        Optional<StepPlanner.Cell> n = planner.next(
                new StepPlanner.Cell(0, 1, 0), new StepPlanner.Cell(5, 1, 0), floorWorld(box));
        assertEquals(Optional.empty(), n);
    }

    @Test void neverReturnsSolidCell() {
        Set<StepPlanner.Cell> wall = Set.of(new StepPlanner.Cell(1, 1, 0));
        Optional<StepPlanner.Cell> n = planner.next(
                new StepPlanner.Cell(0, 1, 0), new StepPlanner.Cell(5, 1, 0), floorWorld(wall));
        n.ifPresent(cell -> assertTrue(floorWorld(wall).isPassable(cell.x(), cell.y(), cell.z())));
    }

    // --- Additional geometry locks (beyond the brief) ---

    @Test void stepsDownToLowerLedge() {
        // Creature stands on a pillar top at (0,2,0); ground ahead is one lower at (1,1,0).
        Set<StepPlanner.Cell> solids = Set.of(
                new StepPlanner.Cell(0, 1, 0), // floor under the creature
                new StepPlanner.Cell(1, 0, 0)); // floor of the lower ledge ahead
        Optional<StepPlanner.Cell> n = planner.next(
                new StepPlanner.Cell(0, 2, 0), new StepPlanner.Cell(5, 2, 0), gridWorld(solids));
        assertEquals(Optional.of(new StepPlanner.Cell(1, 1, 0)), n);
    }

    @Test void flanksAroundABlockedFace() {
        // Forward (+x) is a solid too tall to climb over (ceiling blocks the ascent), the -z flank
        // is walled, but the +z flank is open with floor support: the creature steps sideways.
        Set<StepPlanner.Cell> solids = Set.of(
                new StepPlanner.Cell(0, 0, 0), // floor under the creature
                new StepPlanner.Cell(1, 1, 0), // blocked forward face
                new StepPlanner.Cell(0, 2, 0), // blocks the upward climb
                new StepPlanner.Cell(0, 1, -1), // -z flank walled
                new StepPlanner.Cell(0, 0, 1)); // floor supporting the +z flank
        Optional<StepPlanner.Cell> n = planner.next(
                new StepPlanner.Cell(0, 1, 0), new StepPlanner.Cell(2, 1, 0), gridWorld(solids));
        assertEquals(Optional.of(new StepPlanner.Cell(0, 1, 1)), n);
    }

    @Test void traversesAlongACeiling() {
        // Creature clings under a ceiling with no floor beneath it; the ceiling continues over the
        // forward cell, so it shuffles forward while hanging.
        Set<StepPlanner.Cell> solids = Set.of(
                new StepPlanner.Cell(0, 3, 0), // ceiling above the creature
                new StepPlanner.Cell(1, 3, 0)); // ceiling above the forward cell
        Optional<StepPlanner.Cell> n = planner.next(
                new StepPlanner.Cell(0, 2, 0), new StepPlanner.Cell(5, 2, 0), gridWorld(solids));
        assertEquals(Optional.of(new StepPlanner.Cell(1, 2, 0)), n);
    }

    @Test void ceilingDisabledDoesNotTraverse() {
        // Same clinging geometry, but a planner without ceiling ability finds no move.
        StepPlanner noCeiling = new StepPlanner(1, 3, 24, false);
        Set<StepPlanner.Cell> solids = Set.of(
                new StepPlanner.Cell(0, 3, 0),
                new StepPlanner.Cell(1, 3, 0));
        Optional<StepPlanner.Cell> n = noCeiling.next(
                new StepPlanner.Cell(0, 2, 0), new StepPlanner.Cell(5, 2, 0), gridWorld(solids));
        assertEquals(Optional.empty(), n);
    }
}

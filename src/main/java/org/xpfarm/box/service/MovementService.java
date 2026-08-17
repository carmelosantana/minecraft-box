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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Shulker;
import org.xpfarm.box.config.BoxConfig;
import org.xpfarm.box.config.StageDef;
import org.xpfarm.box.model.BoxState;
import org.xpfarm.box.model.Passability;
import org.xpfarm.box.model.StarvationCurve;
import org.xpfarm.box.model.StepPlanner;

/**
 * Bukkit adapter that drives the pure {@link StepPlanner} against the live world, moving the
 * creature one cell per stage-and-starvation-scaled interval while it is unobserved.
 *
 * <p>The service owns two responsibilities: <em>pacing</em> (how often a step is due) and
 * <em>execution</em> (probing real blocks, planning the one next cell, teleporting onto it, and
 * setting the shulker's attach face). Pacing is pure arithmetic and unit-tested here; execution
 * touches a running server and is a gate-7a obligation.
 *
 * <h2>Pacing</h2>
 *
 * Each stage defines a base {@code step-interval-ticks}; the starvation curve scales it down (the
 * creature accelerates as it starves). {@link #effectiveInterval(int, double)} multiplies the two
 * and rounds, flooring at one tick so the tick-loop modulo can never divide by zero. A step fires
 * only on ticks where {@code tickCounter % interval == 0}.
 *
 * <h2>Passability probe</h2>
 *
 * The planner asks a {@link Passability} probe whether each cell is occupiable. This adapter answers
 * from the live world: a cell is passable when the block there is <strong>not</strong> solid
 * ({@code !block.getType().isSolid()}), which doubles as the planner's floor/wall/ceiling test
 * (a solid block is both an obstacle and support). This is deliberately coarse per the design brief;
 * partial blocks (slabs, stairs, fences, panes) and fluids resolve by {@code isSolid()} alone, which
 * is a candidate gate-7a refinement if playtesting shows the creature treating water or a fence gap
 * as free space.
 *
 * <h2>The three {@link StepPlanner} design behaviors (confirmed against spec §3.3)</h2>
 *
 * Wiring the live probe does not change these, and all three match the design intent:
 *
 * <ul>
 *   <li><b>Stateless climb budget</b> — the planner re-derives a climb every tick and measures the
 *       reachable wall top within {@code maxClimbHeight} above its <em>current</em> height. Because
 *       the creature rises one cell per tick, an arbitrarily tall wall is still surmounted, matching
 *       "walls and trenches do not stop it … a walled compound without a roof is not a defense."
 *   <li><b>Continuous ceiling</b> — ceiling traversal requires a solid block above both the current
 *       and the forward cell, so the creature clings only under an unbroken overhang, matching
 *       "traverse an overhang or ceiling face, arriving from above."
 *   <li><b>One axis at a time</b> — the planner steps along the dominant horizontal axis and, when
 *       blocked head-on, flanks along the perpendicular axis (no diagonals), matching "if blocked
 *       head-on, try the two flanking cells; if fully blocked, wait."
 * </ul>
 *
 * <h2>Gate-7a obligations (live-only, not unit-tested here)</h2>
 *
 * The block probe, teleport, attach-face grip, and sealed detection depend on a running server's
 * blocks and entities, which cannot be faithfully mocked ({@code Material.isSolid()} throws without a
 * registry). They are acceptance checks verified at gate 7a:
 *
 * <ul>
 *   <li><b>Acceptance check 3</b> — while unobserved the creature advances one cell per interval
 *       toward the victim via {@code teleport()}.
 *   <li><b>Acceptance checks 14, 15</b> — it climbs a vertical face (attaching to the wall) and
 *       traverses a ceiling to reach a victim a plain walk could not.
 *   <li><b>Acceptance check 16</b> — a victim in a genuinely sealed volume is unreachable:
 *       {@link #isSealedFrom} reports {@code true} and the creature waits.
 *   <li><b>Acceptance check 17</b> — starvation pacing visibly accelerates the step cadence as the
 *       creature goes unfed.
 * </ul>
 *
 * The unit-testable seams are {@link #effectiveInterval(int, double)} and
 * {@link #stageStepIntervalTicks(BoxConfig, int)}, both pure and covered by
 * {@code MovementServiceTest}.
 */
public final class MovementService {

    /** Horizontal faces probed to find the wall a climbing creature grips, in a stable order. */
    private static final BlockFace[] HORIZONTAL_FACES = {
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final BoxConfig config;
    private final SoundPlayer sounds;
    private final StepPlanner planner;

    /**
     * @param config the validated configuration supplying movement tolerances, stage intervals, and
     *     the starvation curve
     * @param sounds the sound adapter used to play the {@code movement} cue on each step
     */
    public MovementService(BoxConfig config, SoundPlayer sounds) {
        this.config = Objects.requireNonNull(config, "config");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.planner = new StepPlanner(config.maxStepUp(), config.maxStepDown(),
                config.maxClimbHeight(), config.allowCeilingTraversal());
    }

    /**
     * Advances the creature one cell toward the victim when a step is due this tick.
     *
     * <p>Computes the effective interval from the creature's stage and starvation, and returns
     * immediately on any tick that is not a step boundary. On a step boundary it builds a live-world
     * passability probe, asks the {@link StepPlanner} for the single next cell, and — on a present
     * result — teleports the shulker to that cell's center-bottom, sets its attach face, and plays
     * the movement sound. When the planner returns empty (sealed or fully blocked) nothing happens;
     * the caller drives the {@code WAITING} transition via {@link #isSealedFrom}.
     *
     * <p><strong>Live path (gate 7a).</strong> The block probe, teleport, and attach face require a
     * running server and are not unit-tested.
     *
     * @param box the live creature to move (a no-op when {@code null})
     * @param s the creature's mutable state, for its stage and last-fed baseline
     * @param victimLoc the victim's current location (a no-op when {@code null})
     * @param nowSecond the current epoch second, for the starvation elapsed time
     * @param tickCounter the monotonically increasing server tick, for the step-boundary modulo
     */
    public void stepIfDue(Shulker box, BoxState s, Location victimLoc, long nowSecond,
            long tickCounter) {
        if (box == null || s == null || victimLoc == null) {
            return;
        }
        int base = stageStepIntervalTicks(config, s.stageIndex());
        double multiplier = StarvationCurve.multiplier(nowSecond - s.lastFedEpochSecond(), config);
        long interval = effectiveInterval(base, multiplier);
        if (tickCounter % interval != 0L) {
            return;
        }

        Location from = box.getLocation();
        World world = from.getWorld();
        if (world == null) {
            return;
        }
        Optional<StepPlanner.Cell> next = planStep(world, from, victimLoc);
        if (next.isEmpty()) {
            return;
        }
        StepPlanner.Cell to = next.get();

        // Center the block within its cell; keep the shulker's current facing.
        Location dest = new Location(world, to.x() + 0.5, to.y(), to.z() + 0.5,
                from.getYaw(), from.getPitch());
        boolean climbed = to.x() == from.getBlockX()
                && to.z() == from.getBlockZ()
                && to.y() != from.getBlockY();
        box.teleport(dest);
        box.setAttachedFace(attachFace(world, to, climbed));
        sounds.play(dest, "movement");
    }

    /**
     * Whether the victim is currently unreachable from the creature's position — the planner finds
     * no next cell across its full flat/step/climb/ceiling/flank breadth. This is the {@code WAITING}
     * trigger: a genuinely sealed volume returns {@code true}.
     *
     * <p><strong>Live path (gate 7a).</strong> Uses the live block probe; not unit-tested.
     *
     * @param box the live creature (returns {@code false} when {@code null})
     * @param victimLoc the victim's location (returns {@code false} when {@code null})
     * @return {@code true} when the planner returns empty for the current geometry
     */
    public boolean isSealedFrom(Shulker box, Location victimLoc) {
        if (box == null || victimLoc == null) {
            return false;
        }
        Location from = box.getLocation();
        World world = from.getWorld();
        if (world == null) {
            return false;
        }
        return planStep(world, from, victimLoc).isEmpty();
    }

    /**
     * Plans the single next cell from the creature's block position toward the victim's block
     * position, using a live-world passability probe.
     */
    private Optional<StepPlanner.Cell> planStep(World world, Location from, Location victimLoc) {
        StepPlanner.Cell fromCell =
                new StepPlanner.Cell(from.getBlockX(), from.getBlockY(), from.getBlockZ());
        StepPlanner.Cell victimCell = new StepPlanner.Cell(
                victimLoc.getBlockX(), victimLoc.getBlockY(), victimLoc.getBlockZ());
        Passability probe = (x, y, z) -> !world.getBlockAt(x, y, z).getType().isSolid();
        return planner.next(fromCell, victimCell, probe);
    }

    /**
     * The face the shulker should attach to after a move. A climb (purely vertical step) grips the
     * nearest horizontal wall; every other move attaches {@link BlockFace#DOWN} to the floor. When a
     * climb cannot find an adjacent solid face (an edge case at the wall's lip) it falls back to
     * {@code DOWN} rather than leaving the grip unset.
     *
     * <p><strong>Live path (gate 7a).</strong> Reads live blocks via {@code isSolid()}.
     */
    private BlockFace attachFace(World world, StepPlanner.Cell at, boolean climbed) {
        if (!climbed) {
            return BlockFace.DOWN;
        }
        for (BlockFace face : HORIZONTAL_FACES) {
            int wx = at.x() + face.getModX();
            int wz = at.z() + face.getModZ();
            if (world.getBlockAt(wx, at.y(), wz).getType().isSolid()) {
                return face;
            }
        }
        return BlockFace.DOWN;
    }

    /**
     * The base step interval, in ticks, for the given stage, clamping an out-of-range index onto the
     * nearest end of the stage table. Pure lookup over {@link BoxConfig#stages()}.
     *
     * @param config the configuration whose stage table is read
     * @param stageIndex the creature's zero-based stage index (clamped into range)
     * @return the stage's {@code step-interval-ticks}
     */
    public static int stageStepIntervalTicks(BoxConfig config, int stageIndex) {
        List<StageDef> stages = config.stages();
        int clamped = Math.max(0, Math.min(stageIndex, stages.size() - 1));
        return stages.get(clamped).stepIntervalTicks();
    }

    /**
     * The effective step interval: the base stage interval scaled by the starvation multiplier,
     * rounded to the nearest tick and floored at one.
     *
     * <p>The floor is load-bearing: the caller uses this as a modulus, so a zero would divide by
     * zero. A sub-1.0 multiplier (starving) shortens the interval; a &gt;1.0 multiplier lengthens it.
     *
     * @param baseIntervalTicks the stage's base interval in ticks
     * @param starvationMultiplier the {@link StarvationCurve#multiplier} factor
     * @return the scaled interval, always {@code >= 1}
     */
    public static long effectiveInterval(int baseIntervalTicks, double starvationMultiplier) {
        long scaled = Math.round(baseIntervalTicks * starvationMultiplier);
        return Math.max(1L, scaled);
    }
}

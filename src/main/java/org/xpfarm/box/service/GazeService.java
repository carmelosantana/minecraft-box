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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.xpfarm.box.config.BoxConfig;
import org.xpfarm.box.model.GazeMath;

/**
 * Bukkit adapter that answers "who is currently watching the creature?" by wrapping the pure
 * {@link GazeMath} geometry against live players and a live {@link Shulker}.
 *
 * <p>For each candidate the test is applied cheapest-first per the spec's ordering, short-circuiting
 * as soon as a stage fails:
 *
 * <ol>
 *   <li>game-mode eligibility ({@link #shouldConsider(GameMode, boolean, boolean)}, no allocation);
 *   <li>the field-of-view cone ({@link GazeMath#inCone}, a dot product);
 *   <li>the clamped distance ({@link GazeMath#withinDistance}, a squared-distance compare);
 *   <li>only then the expensive line-of-sight raytrace ({@link org.bukkit.World#rayTraceBlocks}).
 * </ol>
 *
 * <p>The gaze target is the <em>center</em> of the shulker's block, not its corner.
 *
 * <h2>Gate-7a obligations (live-only, not unit-tested here)</h2>
 *
 * The live paths below depend on a running server's eye vectors, world geometry, and block
 * raytracing, which cannot be faithfully mocked. They are acceptance checks verified at gate 7a:
 *
 * <ul>
 *   <li><b>Acceptance check 2</b> — a player looking directly at the box within range registers as
 *       a gazer; looking away or standing beyond the clamped range does not.
 *   <li><b>Acceptance check 4</b> — a solid block between the player and the box occludes the gaze
 *       (no gazer); removing it restores the gaze.
 *   <li><b>Acceptance check 5</b> — {@link #isGazedByAny} gates movement: the creature is frozen
 *       while at least one eligible, unobstructed player watches it.
 * </ul>
 *
 * The only unit-testable seam is {@link #shouldConsider(GameMode, boolean, boolean)}, whose truth
 * table is covered by {@code GazeServiceTest}.
 */
public final class GazeService {

    /**
     * Slack, in blocks, on the line-of-sight comparison. A block hit closer to the eye than the box
     * by more than this margin occludes the gaze; a hit at (or effectively at) the box distance is
     * the box's own cell and does not. Absorbs floating-point noise in the raytrace hit position.
     */
    private static final double OCCLUSION_EPSILON = 1.0e-4;

    private final BoxConfig config;

    public GazeService(BoxConfig config) {
        this.config = config;
    }

    /**
     * Whether a candidate in the given game mode is eligible to freeze or bind the creature. A
     * spectator is excluded when {@code ignoreSpectator} is set; a creative player is excluded when
     * {@code ignoreCreative} is set; every other mode (survival, adventure) is always eligible.
     *
     * <p>Pure and side-effect free — the adapter's unit-testable decision seam.
     *
     * @param mode the candidate's current game mode
     * @param ignoreCreative whether creative-mode players cannot gaze
     * @param ignoreSpectator whether spectator-mode players cannot gaze
     * @return {@code true} if the candidate should be considered a potential gazer
     */
    public static boolean shouldConsider(GameMode mode, boolean ignoreCreative,
            boolean ignoreSpectator) {
        if (ignoreSpectator && mode == GameMode.SPECTATOR) {
            return false;
        }
        if (ignoreCreative && mode == GameMode.CREATIVE) {
            return false;
        }
        return true;
    }

    /**
     * Every candidate currently gazing at the box: eligible by game mode, with the box center inside
     * their view cone, within the clamped gaze range, and with an unobstructed line of sight.
     *
     * @param box the live creature being watched
     * @param candidates the players to test (typically the box's viewers within tracking range)
     * @param trackingRange the server's entity tracking range in blocks, used to clamp gaze range
     * @return a new list of the gazers, in candidate iteration order (empty if none)
     */
    public List<Player> gazers(Shulker box, Collection<? extends Player> candidates,
            double trackingRange) {
        List<Player> result = new ArrayList<>();
        Vector boxCenter = box.getLocation().toCenterLocation().toVector();
        double maxDistance = GazeMath.effectiveMaxDistance(config.gazeMaxDistance(), trackingRange);
        for (Player candidate : candidates) {
            if (isGazing(box, boxCenter, candidate, maxDistance)) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * Whether at least one candidate is gazing at the box. Short-circuits on the first gazer; the
     * movement gate only needs existence, so this avoids building a list or testing the rest.
     *
     * @param box the live creature being watched
     * @param players the players to test
     * @param trackingRange the server's entity tracking range in blocks, used to clamp gaze range
     * @return {@code true} if any player is gazing at the box
     */
    public boolean isGazedByAny(Shulker box, Collection<? extends Player> players,
            double trackingRange) {
        Vector boxCenter = box.getLocation().toCenterLocation().toVector();
        double maxDistance = GazeMath.effectiveMaxDistance(config.gazeMaxDistance(), trackingRange);
        for (Player player : players) {
            if (isGazing(box, boxCenter, player, maxDistance)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The cheapest-first gaze test for one candidate. Ordered so each cheaper stage can reject the
     * candidate before a more expensive one runs: mode check, then cone dot product, then squared
     * distance, then the line-of-sight raytrace.
     */
    private boolean isGazing(Shulker box, Vector boxCenter, Player candidate, double maxDistance) {
        if (!shouldConsider(candidate.getGameMode(), config.ignoreCreative(),
                config.ignoreSpectator())) {
            return false;
        }
        Location eyeLoc = candidate.getEyeLocation();
        Vector eye = eyeLoc.toVector();
        Vector look = eyeLoc.getDirection();
        if (!GazeMath.inCone(eye, look, boxCenter, config.fovCosine())) {
            return false;
        }
        if (!GazeMath.withinDistance(eye, boxCenter, maxDistance)) {
            return false;
        }
        return hasLineOfSight(eyeLoc, eye, boxCenter, maxDistance);
    }

    /**
     * Whether nothing solid stands between the eye and the box center. Casts a ray from the eye
     * toward the box, no farther than the box itself (clamped to the effective gaze range). An
     * unobstructed cast returns {@code null} (nothing hit within range); otherwise the gaze is
     * blocked only when the hit lands strictly before the box, not at the box's own cell.
     *
     * <p>{@link FluidCollisionMode#NEVER} keeps water and lava transparent to the gaze;
     * {@code ignorePassableBlocks = true} keeps grass, torches, and the like transparent too — only
     * solid, sight-blocking blocks occlude.
     */
    private boolean hasLineOfSight(Location eyeLoc, Vector eye, Vector boxCenter,
            double maxDistance) {
        double boxDistance = Math.min(eye.distance(boxCenter), maxDistance);
        Vector direction = boxCenter.clone().subtract(eye);
        if (direction.lengthSquared() == 0.0) {
            // Eye is on the box center; nothing can be between them.
            return true;
        }
        direction.normalize();
        RayTraceResult hit = eyeLoc.getWorld().rayTraceBlocks(eyeLoc, direction, boxDistance,
                FluidCollisionMode.NEVER, true);
        if (hit == null) {
            return true;
        }
        double hitDistance = eye.distance(hit.getHitPosition());
        return hitDistance >= boxDistance - OCCLUSION_EPSILON;
    }
}

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

import org.bukkit.util.Vector;

/**
 * Pure gaze geometry: the correctness heart that decides whether a player is looking at the
 * creature and whether they are close enough for the gaze to count.
 *
 * <p>Every method is a pure static function of its {@link Vector} arguments. {@code Vector} is a
 * Bukkit value type that needs no running server, so this class is fully unit-testable. Inputs are
 * never mutated: {@link Vector#normalize()} and {@link Vector#subtract(Vector)} mutate in place and
 * return {@code this}, so this class operates on {@link Vector#clone() clones} to avoid corrupting
 * caller state.
 */
public final class GazeMath {

    private GazeMath() {
    }

    /**
     * Reports whether {@code target} lies inside the view cone whose axis is {@code look} and whose
     * half-angle has cosine {@code fovCosine}.
     *
     * <p>Computes the unit direction from {@code eye} to {@code target} and compares it against the
     * normalized look direction: the target is in the cone when
     * {@code look.dot(dir) >= fovCosine}. A larger {@code fovCosine} is a narrower cone.
     *
     * @param eye the observer's eye position
     * @param look the observer's look direction (need not be unit length)
     * @param target the point being tested
     * @param fovCosine cosine of the cone's half-angle
     * @return {@code true} if {@code target} is within the cone; {@code false} if {@code target}
     *     equals {@code eye} (the direction is undefined — you are not looking at something you are
     *     standing inside)
     */
    public static boolean inCone(Vector eye, Vector look, Vector target, double fovCosine) {
        Vector dir = target.clone().subtract(eye);
        if (dir.lengthSquared() == 0.0) {
            return false;
        }
        return look.clone().normalize().dot(dir.normalize()) >= fovCosine;
    }

    /**
     * Reports whether {@code target} is within {@code maxDistance} blocks of {@code eye}, using a
     * squared-distance comparison to avoid a square root.
     *
     * @param eye the observer's position
     * @param target the point being tested
     * @param maxDistance the inclusive maximum distance in blocks
     * @return {@code true} if the distance from {@code eye} to {@code target} is at most
     *     {@code maxDistance}
     */
    public static boolean withinDistance(Vector eye, Vector target, double maxDistance) {
        return eye.distanceSquared(target) <= maxDistance * maxDistance;
    }

    /**
     * Clamps the configured gaze distance to the entity's tracking range. A player farther than the
     * server ever sends the entity cannot be freezing it, so the effective range is the smaller of
     * the two.
     *
     * @param configured the configured gaze max distance in blocks
     * @param trackingRange the server's entity tracking range in blocks
     * @return the smaller of the two distances
     */
    public static double effectiveMaxDistance(double configured, double trackingRange) {
        return Math.min(configured, trackingRange);
    }
}

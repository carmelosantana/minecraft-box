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
 * Pure vanilla-Minecraft experience-<em>point</em> math. Services bank and return experience
 * as points so they never have to reason about {@code getLevel()} plus a progress bar.
 *
 * <p>The two piecewise curves are the canonical vanilla ones and are mutually consistent:
 * {@code pointsForLevel(level + 1) - pointsForLevel(level) == pointsToNext(level)} holds for
 * every {@code level >= 0}. The cumulative pieces meet exactly at their shared boundary
 * levels (16 and 31 each satisfy both neighbouring formulas), so no rounding fudge is needed.
 * No Bukkit is involved.
 */
public final class Xp {

    private Xp() {
    }

    /**
     * Cumulative experience points required to reach {@code level} from zero, per the vanilla
     * curve. {@code pointsForLevel(0)} is {@code 0}.
     *
     * <ul>
     *   <li>{@code level <= 16}: {@code level^2 + 6*level}</li>
     *   <li>{@code 17..31}: {@code 2.5*level^2 - 40.5*level + 360}</li>
     *   <li>{@code >= 32}: {@code 4.5*level^2 - 162.5*level + 2220}</li>
     * </ul>
     *
     * @param level the level to reach
     * @return total points to reach {@code level}
     */
    public static int pointsForLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        }
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    /**
     * Points needed to advance from {@code level} to {@code level + 1}, per the vanilla curve.
     *
     * <ul>
     *   <li>{@code level <= 15}: {@code 2*level + 7}</li>
     *   <li>{@code 16..30}: {@code 5*level - 38}</li>
     *   <li>{@code >= 31}: {@code 9*level - 158}</li>
     * </ul>
     *
     * @param level the level being advanced from
     * @return points to reach the next level
     */
    public static int pointsToNext(int level) {
        if (level <= 15) {
            return 2 * level + 7;
        }
        if (level <= 30) {
            return 5 * level - 38;
        }
        return 9 * level - 158;
    }

    /**
     * Total experience points held by a player at {@code level} with the XP bar {@code progress}
     * of the way to the next level: {@code pointsForLevel(level) + round(progress * pointsToNext(level))}.
     *
     * @param level the player's current level
     * @param progress the {@code 0..1} XP-bar fraction (as from {@code Player#getExp()})
     * @return total experience points
     */
    public static int totalPointsAt(int level, float progress) {
        return pointsForLevel(level) + Math.round(progress * pointsToNext(level));
    }

    /**
     * The share of {@code banked} points returned at the given {@code ratio}, rounded to the
     * nearest whole point ({@code Math.round(banked * ratio)}).
     *
     * @param banked the banked point total
     * @param ratio the return ratio (typically {@code 0..1})
     * @return the returned point total
     */
    public static long returnedPoints(long banked, double ratio) {
        return Math.round(banked * ratio);
    }
}

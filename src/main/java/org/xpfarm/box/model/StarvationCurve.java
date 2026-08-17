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

import org.xpfarm.box.config.BoxConfig;

/**
 * Pure starvation-curve math. As the creature goes unfed its step interval and its ambient
 * volume ramp linearly from neutral to their configured extremes.
 *
 * <p>Both curves are neutral ({@code 1.0}) below {@code onsetSeconds}, ramp linearly across
 * {@code [onsetSeconds, maxSeconds]}, and clamp beyond {@code maxSeconds}. No Bukkit is
 * involved. When starvation is disabled the neutral {@code 1.0} is returned throughout.
 */
public final class StarvationCurve {

    private StarvationCurve() {
    }

    /**
     * The step-interval factor at {@code unfedSeconds}: {@code 1.0} until onset, ramping
     * linearly to {@code c.stepIntervalMultiplier()} at {@code maxSeconds}, clamped beyond.
     *
     * @param unfedSeconds seconds since the creature last fed
     * @param c the active configuration
     * @return the step-interval multiplier
     */
    public static double multiplier(long unfedSeconds, BoxConfig c) {
        return ramp(unfedSeconds, c, c.stepIntervalMultiplier());
    }

    /**
     * The ambient-volume factor at {@code unfedSeconds}, ramping symmetrically toward
     * {@code c.volumeMultiplier()} on the same schedule as {@link #multiplier}.
     *
     * @param unfedSeconds seconds since the creature last fed
     * @param c the active configuration
     * @return the volume multiplier
     */
    public static double volume(long unfedSeconds, BoxConfig c) {
        return ramp(unfedSeconds, c, c.volumeMultiplier());
    }

    /**
     * Linearly interpolates from {@code 1.0} at {@code onsetSeconds} to {@code target} at
     * {@code maxSeconds}, clamped to {@code 1.0} below onset and to {@code target} beyond max.
     * A zero-width {@code [onset, max]} window yields {@code target} at or past onset.
     */
    private static double ramp(long unfedSeconds, BoxConfig c, double target) {
        if (!c.starvationEnabled()) {
            return 1.0;
        }
        long onset = c.onsetSeconds();
        long max = c.maxSeconds();
        if (unfedSeconds <= onset) {
            return 1.0;
        }
        if (unfedSeconds >= max) {
            return target;
        }
        double t = (double) (unfedSeconds - onset) / (double) (max - onset);
        return 1.0 + t * (target - 1.0);
    }
}

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.xpfarm.box.config.BoxConfig;
import org.xpfarm.box.config.StageDef;
import org.xpfarm.box.model.StarvationCurve;
import org.xpfarm.box.testutil.Configs;

/**
 * Unit tests for the pure seams of {@link MovementService}: the interval arithmetic
 * ({@link MovementService#effectiveInterval}) and the stage step-interval lookup
 * ({@link MovementService#stageStepIntervalTicks}).
 *
 * <p>The live movement surface — {@code stepIfDue}, {@code isSealedFrom}, the {@code isSolid}
 * passability probe, the {@code teleport}, and the {@code setAttachedFace} climb grip — depends on a
 * running server's blocks and entities and is a gate-7a obligation (acceptance checks 3, 14, 15, 16,
 * 17). It is documented in the service Javadoc, not mocked here: {@code Material.isSolid()} throws
 * {@code IllegalStateException} without a registry, so a headless block probe cannot be faithfully
 * exercised.
 */
class MovementServiceTest {

    @Nested
    class EffectiveInterval {

        @Test
        void neutralMultiplierLeavesBaseUnchanged() {
            assertEquals(20L, MovementService.effectiveInterval(20, 1.0));
        }

        @Test
        void starvationHalvesTheInterval() {
            // A 0.5 multiplier (full starvation) accelerates: the creature steps twice as often.
            assertEquals(10L, MovementService.effectiveInterval(20, 0.5));
        }

        @Test
        void slowingMultiplierLengthensTheInterval() {
            assertEquals(30L, MovementService.effectiveInterval(20, 1.5));
        }

        @Test
        void roundsToNearestTick() {
            assertEquals(2L, MovementService.effectiveInterval(3, 0.5));   // round(1.5) -> 2
            assertEquals(3L, MovementService.effectiveInterval(5, 0.5));   // round(2.5) -> 3
            assertEquals(4L, MovementService.effectiveInterval(7, 0.5));   // round(3.5) -> 4
        }

        @Test
        void neverDropsBelowOneTick() {
            // A zero (or tiny) product would divide the tick loop by zero; the floor guards it.
            assertEquals(1L, MovementService.effectiveInterval(20, 0.0));
            assertEquals(1L, MovementService.effectiveInterval(1, 0.4)); // round(0.4) -> 0 -> 1
            assertEquals(1L, MovementService.effectiveInterval(0, 1.0));
        }

        @Test
        void composesWithTheStarvationCurveAtFullStarvation() {
            // At/after maxSeconds the curve returns the configured step-interval multiplier (0.5),
            // so a 20-tick base stage steps every 10 ticks when fully starved.
            BoxConfig c = Configs.withStarvation(true, 300, 1800, 0.5, 1.5);
            double mult = StarvationCurve.multiplier(5000, c);
            assertEquals(10L, MovementService.effectiveInterval(20, mult));
        }

        @Test
        void composesWithTheStarvationCurveBeforeOnset() {
            // Below onset the curve is neutral (1.0): the base interval is used unchanged.
            BoxConfig c = Configs.withStarvation(true, 300, 1800, 0.5, 1.5);
            double mult = StarvationCurve.multiplier(60, c);
            assertEquals(20L, MovementService.effectiveInterval(20, mult));
        }
    }

    @Nested
    class EffectiveIntervalIterations {

        // The pacing unit stepIfDue actually uses. FEED_PERIOD_TICKS == 2 is the loop period.
        private static final long PERIOD = FeedingService.FEED_PERIOD_TICKS;

        @Test
        void convertsTickIntervalToWholeIterations() {
            // 20 ticks / period 2 == 10 iterations; 8 ticks == 4 iterations.
            assertEquals(10L, MovementService.effectiveIntervalIterations(20, 1.0, PERIOD));
            assertEquals(4L, MovementService.effectiveIntervalIterations(8, 1.0, PERIOD));
        }

        @Test
        void neverDropsBelowOneIteration() {
            assertEquals(1L, MovementService.effectiveIntervalIterations(20, 0.0, PERIOD));
            assertEquals(1L, MovementService.effectiveIntervalIterations(1, 1.0, PERIOD));
        }

        @Test
        void oddTickIntervalStillPacesAtFullRate() {
            // The bug this fixes: a raw odd tick-interval (e.g. 17) against an even server-tick
            // counter fired only every 34 ticks. In iteration units 17 ticks -> round(8.5) == 9
            // iterations == 18 ticks, i.e. full rate, not half.
            assertEquals(9L, MovementService.effectiveIntervalIterations(17, 1.0, PERIOD));
            assertEquals(8L, MovementService.effectiveIntervalIterations(15, 1.0, PERIOD));
        }

        @Test
        void ticksBetweenStepsNeverIncreaseAsStarvationDeepens() {
            // The correctness property (spec §3.7, acceptance check 17): as the starvation multiplier
            // falls from 1.0 to the configured floor, a hungrier creature must never step SLOWER.
            // Sweep finely so every multiplier that yields an odd raw tick-interval is covered.
            int base = 20;
            double floor = 0.5; // starvation.step-interval-multiplier default
            long prevTicksBetween = Long.MIN_VALUE;
            long worst = -1;
            for (int step = 0; step <= 1000; step++) {
                double mult = 1.0 - (1.0 - floor) * (step / 1000.0); // 1.0 down to 0.5
                long iterations = MovementService.effectiveIntervalIterations(base, mult, PERIOD);
                long ticksBetween = iterations * PERIOD;
                if (prevTicksBetween != Long.MIN_VALUE) {
                    assertTrue(ticksBetween <= prevTicksBetween,
                            "ticks-between-steps increased as starvation deepened: mult=" + mult
                                    + " gave " + ticksBetween + " after " + prevTicksBetween);
                }
                prevTicksBetween = ticksBetween;
                worst = ticksBetween;
            }
            // Sanity: full starvation is strictly faster than neutral for this base.
            assertTrue(worst < 20 * PERIOD, "full starvation should be faster than neutral");
        }

        @Test
        void spotChecksTheHistoricInversionCase() {
            // 20*0.85 -> round(17)=17 ticks -> 9 iterations -> 18 ticks; must be <= the neutral 20.
            long neutral = MovementService.effectiveIntervalIterations(20, 1.0, PERIOD) * PERIOD;
            long starving = MovementService.effectiveIntervalIterations(20, 0.85, PERIOD) * PERIOD;
            assertTrue(starving <= neutral,
                    "starving (" + starving + ") must not exceed neutral (" + neutral + ")");
        }
    }

    @Nested
    class StageStepIntervalTicks {

        private final BoxConfig config = Configs.defaults();

        @Test
        void readsEachStagesConfiguredInterval() {
            // Default stages: 20, 14, 8 ticks at indices 0, 1, 2.
            assertEquals(20, MovementService.stageStepIntervalTicks(config, 0));
            assertEquals(14, MovementService.stageStepIntervalTicks(config, 1));
            assertEquals(8, MovementService.stageStepIntervalTicks(config, 2));
        }

        @Test
        void clampsAnIndexAboveTheTableToTheLastStage() {
            assertEquals(8, MovementService.stageStepIntervalTicks(config, 3));
            assertEquals(8, MovementService.stageStepIntervalTicks(config, 99));
        }

        @Test
        void clampsANegativeIndexToTheFirstStage() {
            assertEquals(20, MovementService.stageStepIntervalTicks(config, -1));
        }

        @Test
        void readsAnOverriddenSingleStageTable() {
            BoxConfig single = Configs.withStages(
                    List.of(new StageDef(1, 0L, 20.0, 5, 6.0, false)));
            assertEquals(5, MovementService.stageStepIntervalTicks(single, 0));
            // Every out-of-range index collapses onto the sole stage.
            assertEquals(5, MovementService.stageStepIntervalTicks(single, 7));
        }
    }
}

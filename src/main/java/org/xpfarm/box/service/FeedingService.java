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

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.xpfarm.box.config.BoxConfig;
import org.xpfarm.box.config.StageDef;
import org.xpfarm.box.model.BoxState;
import org.xpfarm.box.model.StageTable;
import org.xpfarm.box.model.Xp;
import org.xpfarm.box.persistence.BoxCodec;
import org.xpfarm.box.persistence.BoxKeys;

/**
 * Bukkit adapter that runs the feeding rule: the creature opens (and becomes vulnerable) only for a
 * watcher who has experience to drain, drains that watcher's experience <em>in points</em> into the
 * creature's bank, and grows the creature through its {@link StageTable} as the bank fills.
 *
 * <p>This is the heart of the exploit-closer. A watcher with no XP under {@code requireXpToOpen}
 * leaves the shell sealed, so a drained-dry player cannot use the creature as an invulnerable pet.
 * That decision is extracted to the pure {@link #qualifies(boolean, boolean, int)} predicate, and
 * the per-tick drain amount to the pure {@link #drainWithCarry(double, double[])} accumulator; both
 * are unit-tested exhaustively.
 *
 * <h2>Exact drain rate at any loop period (fractional accumulator)</h2>
 *
 * A configured rate like {@code feeding.xp-per-second = 8} run every {@link #FEED_PERIOD_TICKS}
 * ticks owes {@code 8 * 2 / 20 = 0.8} points per invocation — not an integer. Rounding each tick
 * (e.g. a min-1 floor) would overshoot: 1 point every 2 ticks is 10/s, a 25% error on the shipped
 * default. Instead each creature carries a fractional remainder in {@link #drainCarry}: every tick
 * the exact fractional owe is added to the carry, the whole part is drained, and the sub-1.0
 * remainder is kept for next tick. Over any whole second the drained total equals the configured
 * per-second rate exactly, at any loop period. The carry is transient live-session state keyed by
 * the creature's live entity id, never persisted, and cleared on {@link #close(Shulker)}.
 *
 * <h2>Experience is drained in points, never levels</h2>
 *
 * The amount banked always equals the amount removed from the player, established by
 * <em>measurement</em>, not estimate. Each tick the service reads the gazer's total points before
 * and after the drain via {@link Xp#totalPointsAt(int, float)}, applies the whole-part drain with
 * {@link Player#giveExp(int)} passing the negated amount, and banks the measured
 * {@code before - after} difference with {@link BoxState#bank(long)}. Measuring the real delta means
 * that even on the final near-empty tick — where {@code giveExp} floors the player at zero and may
 * remove fewer points than asked — the banked amount is exactly what left the player.
 *
 * <p><b>Why {@code giveExp(-n)} and not {@code setTotalExperience}:</b> Bukkit's
 * {@code Player#setTotalExperience(int)} is a well-known trap — it writes the lifetime-total display
 * counter without recomputing the level and bar, so it does not reliably set a player's spendable
 * points. Paper's {@code giveExp(int)} adjusts level and bar together and is point-accurate for
 * negative deltas, flooring the player at zero. Banking the measured before/after difference makes
 * banked always equal removed regardless of that floor (acceptance check 9).
 *
 * <h2>Gate-7a obligations (live-only, not unit-tested here)</h2>
 *
 * The live effects of {@link #feedTick} depend on a running server's entities, world, and player XP
 * state, which cannot be faithfully mocked. They are acceptance checks verified against a live
 * client at gate 7a:
 *
 * <ul>
 *   <li><b>Acceptance check 8</b> — the shell opens (peek 1.0) and becomes vulnerable only for a
 *       qualifying gazer; a no-XP gazer under {@code requireXpToOpen} leaves it sealed and
 *       invulnerable. The decision is {@link #qualifies(boolean, boolean, int)} (unit-tested); the
 *       live peek and vulnerability are 7a.
 *   <li><b>Acceptance check 9</b> — the points removed from the gazer equal the points banked, and
 *       the gazer is never driven below zero. The accumulator arithmetic is unit-tested; the live
 *       measured XP transfer via {@link Player#giveExp(int)} is 7a.
 *   <li><b>Acceptance check 10</b> — crossing a stage threshold applies the new stage's max health
 *       to the creature (and a cosmetic scale change), verified against a live entity at 7a.
 * </ul>
 *
 * The only unit-testable seams are {@link #qualifies(boolean, boolean, int)} and
 * {@link #drainWithCarry(double, double[])}, covered by {@code FeedingLogicTest}.
 */
public final class FeedingService {

    /**
     * The tick cadence {@link #feedTick} assumes when computing its per-tick drain. The scheduler
     * that drives feeding must invoke {@code feedTick} once every this many ticks so the long-run
     * drain matches {@code feeding.xp-per-second}. Exposed so the scheduler and the drain math
     * agree on a single number.
     */
    public static final int FEED_PERIOD_TICKS = 2;

    /** Particles streamed toward the gazer per feeding tick (cosmetic; not load-bearing). */
    private static final int FEED_PARTICLE_COUNT = 6;

    private final BoxConfig config;
    private final StageTable stages;
    private final SoundPlayer sounds;
    // held for the tick loop (Task 15) to persist mutated state; not read here
    private final BoxKeys keys;
    private final BoxCodec codec;

    /**
     * Per-creature fractional drain remainder, keyed by the live entity id, so a non-integer
     * per-tick owe (e.g. 0.8 pts at 8/s on a 2-tick period) averages to the exact configured rate
     * over time instead of rounding up every tick. Transient live-session state, never persisted;
     * an entry is removed on {@link #close(Shulker)}.
     */
    private final Map<UUID, Double> drainCarry = new ConcurrentHashMap<>();

    /**
     * @param config the validated configuration (feed rate, {@code requireXpToOpen}, stages, audio)
     * @param stages the growth-stage lookup built from {@code config.stages()}
     * @param sounds the sound adapter used to play the feeding cue
     * @param keys the shared PDC keys (held for parity with sibling services; used by the codec)
     * @param codec the state codec (held so the caller can persist post-feed state through one
     *     collaborator set)
     */
    public FeedingService(BoxConfig config, StageTable stages, SoundPlayer sounds, BoxKeys keys,
            BoxCodec codec) {
        this.config = Objects.requireNonNull(config, "config");
        this.stages = Objects.requireNonNull(stages, "stages");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /**
     * The exploit-closing decision, pure and side-effect free: whether the creature should open for
     * a candidate gazer this tick.
     *
     * <ul>
     *   <li>No gazer in feed reach — never opens.</li>
     *   <li>{@code requireXp} on and the gazer has {@code <= 0} points — stays sealed (the
     *       exploit-closer: a drained-dry watcher cannot keep it open and invulnerable).</li>
     *   <li>Otherwise — opens.</li>
     * </ul>
     *
     * @param gazerInReach whether an eligible gazer is within the current stage's feed radius
     * @param requireXp the {@code feeding.require-xp-to-open} setting
     * @param gazerPoints the gazer's current total experience points (a non-positive value counts
     *     as "no XP to drain")
     * @return {@code true} if the creature should open and feed this tick
     */
    public static boolean qualifies(boolean gazerInReach, boolean requireXp, int gazerPoints) {
        if (!gazerInReach) {
            return false;
        }
        if (requireXp && gazerPoints <= 0) {
            return false;
        }
        return true;
    }

    /**
     * The whole points to drain this tick given the exact fractional points owed and a running
     * fractional carry, so a non-integer per-tick owe averages to the exact rate over time.
     *
     * <p>Pure and deterministic: adds {@code owedThisTick} (clamped at zero) to the incoming carry,
     * drains the whole part, and writes the sub-1.0 remainder back into {@code carryInOut[0]} for
     * the next tick. Because only the fractional remainder is carried, the drained total over any
     * span equals the summed owe to within one point, and over any whole number of seconds equals
     * the configured per-second rate exactly. The return is never negative.
     *
     * @param owedThisTick exact points owed this tick, typically
     *     {@code xpPerSecond * FEED_PERIOD_TICKS / 20.0}
     * @param carryInOut a one-element array holding the fractional remainder; read on entry and
     *     updated in place to the new remainder ({@code 0.0 <= carry < 1.0})
     * @return whole points to drain this tick, {@code >= 0}
     */
    public static int drainWithCarry(double owedThisTick, double[] carryInOut) {
        double total = carryInOut[0] + Math.max(0.0, owedThisTick);
        long whole = (long) Math.floor(total);
        if (whole < 0L) {
            whole = 0L;
        }
        carryInOut[0] = total - whole;
        return whole > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) whole;
    }

    /**
     * Whether the creature should open for the given in-reach gazer, applying the pure
     * {@link #qualifies(boolean, boolean, int)} rule to a live player. A {@code null} gazer means
     * none is in reach.
     *
     * @param s the creature's state (unused today; accepted so callers pass the full context and
     *     future rules can consult it)
     * @param gazerInReach the eligible gazer within feed radius, or {@code null} if none
     * @return {@code true} if the creature should open and feed
     */
    public boolean shouldOpen(BoxState s, @Nullable Player gazerInReach) {
        if (gazerInReach == null) {
            return qualifies(false, config.requireXpToOpen(), 0);
        }
        int points = Xp.totalPointsAt(gazerInReach.getLevel(), gazerInReach.getExp());
        return qualifies(true, config.requireXpToOpen(), points);
    }

    /**
     * Runs one feeding tick against a live creature and gazer: opens the shell, drains experience in
     * points, banks exactly what was drained, streams particles toward the gazer, plays the feeding
     * cue, and grows the creature a stage when the bank crosses a threshold.
     *
     * <p>Callers must invoke this only for a gazer that {@link #shouldOpen(BoxState, Player)}
     * accepts, and once every {@link #FEED_PERIOD_TICKS} ticks so the drain rate matches the config.
     * The XP transfer is point-accurate and floored at the gazer's zero (see the class Javadoc);
     * the banked amount equals the removed amount. Live behaviour is a gate-7a obligation.
     *
     * @param box the live creature to open and grow
     * @param s the creature's mutable state (bank and stage index are updated in place)
     * @param gazer the qualifying watcher whose experience is drained
     * @param tickCounter the global tick counter (used only to vary cosmetic particle placement;
     *     not load-bearing)
     */
    public void feedTick(Shulker box, BoxState s, Player gazer, long tickCounter) {
        // Open and expose: a fully-peeked shulker is vulnerable, which is the point.
        box.setPeek(1.0f);

        // Exact fractional points owed this tick, run through the per-creature carry so the long-run
        // rate is exact at any loop period (no per-tick rounding overshoot).
        double owed = config.xpPerSecond() * (double) FEED_PERIOD_TICKS / 20.0;
        UUID id = box.getUniqueId();
        double[] carry = {drainCarry.getOrDefault(id, 0.0)};
        int wanted = drainWithCarry(owed, carry);
        drainCarry.put(id, carry[0]);

        if (wanted > 0) {
            // Measure the real points removed rather than estimating: Paper's giveExp floors the
            // player at zero, so on the final near-empty tick fewer than `wanted` may leave. Banking
            // the measured before/after difference makes banked == removed exactly (never levels).
            int before = Xp.totalPointsAt(gazer.getLevel(), gazer.getExp());
            gazer.giveExp(-wanted);
            int after = Xp.totalPointsAt(gazer.getLevel(), gazer.getExp());
            long removed = Math.max(0L, (long) before - after);
            s.bank(removed);
        }

        streamFeedParticles(box, gazer, tickCounter);
        sounds.play(box.getLocation(), "feeding");
        applyStageIfChanged(box, s);
    }

    /**
     * Closes the shell (peek 0), sealing the creature so it is no longer open or vulnerable.
     *
     * @param box the live creature to close
     */
    public void close(Shulker box) {
        box.setPeek(0.0f);
        // Drop the transient fractional carry so the map does not leak entries for closed creatures.
        drainCarry.remove(box.getUniqueId());
    }

    /**
     * Drops the transient fractional drain-carry for a creature id without touching any live entity.
     * The removal counterpart to {@link #close(Shulker)} for the paths where the {@link Shulker}
     * handle is unavailable — a creature removed while its chunk is unloaded, or purged by id — so a
     * carry entry can never be stranded (obligation B). A no-op when no entry exists.
     *
     * @param id the creature's entity id whose carry, if any, is dropped
     */
    public void forget(UUID id) {
        drainCarry.remove(id);
    }

    /**
     * Reconciles the drain-carry map against the set of still-live creature ids, dropping every entry
     * whose creature is no longer tracked. Called once per tick-loop iteration as the single, complete
     * safety net for obligation B: whatever path removed a creature — death (which untracks without a
     * {@link #close(Shulker)}), silent despawn, {@code /box purge}, or untrack — its carry cannot
     * outlive it. Cheap: bounded by the live-creature count.
     *
     * @param liveIds the entity ids of every currently-tracked creature
     */
    public void retainOnly(java.util.Set<UUID> liveIds) {
        drainCarry.keySet().retainAll(liveIds);
    }

    /**
     * Recomputes the growth stage from the current bank and, when it changed, applies the new
     * stage's max health to the creature and a cosmetic scale change. Scale is best-effort only.
     */
    private void applyStageIfChanged(Shulker box, BoxState s) {
        StageDef stage = stages.stageFor(s.bankedXp());
        int newIndex = config.stages().indexOf(stage);
        if (newIndex < 0 || newIndex == s.stageIndex()) {
            return;
        }
        s.setStageIndex(newIndex);

        AttributeInstance maxHealth = box.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(stage.maxHealth());
            // Never let current health exceed the new cap (a shrink would otherwise throw on set).
            if (box.getHealth() > stage.maxHealth()) {
                box.setHealth(stage.maxHealth());
            }
        }

        applyScaleCosmetic(box, newIndex);
    }

    /**
     * Applies a cosmetic {@code SCALE} attribute so the creature visibly grows with its stage. This
     * is COSMETIC-ONLY and must never be load-bearing: {@code SCALE} is unverified on Bedrock/Geyser
     * and any failure here is swallowed so it can never break feeding.
     */
    private void applyScaleCosmetic(Shulker box, int stageIndex) {
        try {
            AttributeInstance scale = box.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(1.0 + 0.25 * stageIndex);
            }
        } catch (RuntimeException | LinkageError cosmeticOnly) {
            // Intentionally ignored: scale is decorative and must not affect the feeding rule.
        }
    }

    /** Streams a short burst of particles from the shell toward the gazer (purely cosmetic). */
    private void streamFeedParticles(Shulker box, Player gazer, long tickCounter) {
        World world = box.getWorld();
        if (world == null) {
            return;
        }
        Location origin = box.getLocation().add(0.0, 0.5, 0.0);
        Vector toward = gazer.getEyeLocation().toVector().subtract(origin.toVector());
        double spread = 0.3 + 0.05 * (tickCounter & 3L); // tiny cosmetic variation, not load-bearing
        if (toward.lengthSquared() > 1.0e-6) {
            toward.normalize();
            origin.add(toward.multiply(0.4));
        }
        world.spawnParticle(Particle.SCULK_CHARGE_POP, origin, FEED_PARTICLE_COUNT,
                spread, spread, spread, 0.0);
    }
}

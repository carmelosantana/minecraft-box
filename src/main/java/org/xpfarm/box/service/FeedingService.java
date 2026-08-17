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

import java.util.Objects;
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
 * the per-tick drain amount to {@link #drainThisTick(int, int)}; both are unit-tested exhaustively.
 *
 * <h2>Experience is drained in points, never levels</h2>
 *
 * The amount banked always equals the amount removed from the player. Each tick the service reads
 * the gazer's <em>current total points</em> from their level and XP-bar fraction via
 * {@link Xp#totalPointsAt(int, float)}, computes the tick's drain, <em>clamps it to what the player
 * actually has</em> so it can never push them below zero, applies it with {@link Player#giveExp(int)}
 * passing the negated amount, and banks exactly that clamped amount with {@link BoxState#bank(long)}.
 *
 * <p><b>Why {@code giveExp(-n)} and not {@code setTotalExperience}:</b> Bukkit's
 * {@code Player#setTotalExperience(int)} is a well-known trap — it writes the lifetime-total display
 * counter without recomputing the level and bar, so it does not reliably set a player's spendable
 * points. Paper's {@code giveExp(int)} adjusts level and bar together and is point-accurate for
 * negative deltas, removing exactly {@code n} points when the player has at least {@code n}. Because
 * this service clamps the drain to the player's current total <em>before</em> negating it, the
 * player always has at least the drained amount, so {@code giveExp(-drained)} removes precisely the
 * banked amount — banked always equals removed (acceptance check 9).
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
 *       the gazer is never driven below zero. The arithmetic is unit-tested; the live XP transfer
 *       via {@link Player#giveExp(int)} is 7a.
 *   <li><b>Acceptance check 10</b> — crossing a stage threshold applies the new stage's max health
 *       to the creature (and a cosmetic scale change), verified against a live entity at 7a.
 * </ul>
 *
 * The only unit-testable seams are {@link #qualifies(boolean, boolean, int)} and
 * {@link #drainThisTick(int, int)}, covered by {@code FeedingLogicTest}.
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
    private final BoxKeys keys;
    private final BoxCodec codec;

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
     * Points drained in a single feeding tick given a per-second rate and the tick period.
     *
     * <p>The exact rate is {@code perSecond * period / 20} (20 ticks per second). This is floored to
     * a whole number of points, but with a documented minimum: whenever {@code perSecond > 0} and
     * {@code period > 0}, at least {@code 1} point is drained so a small rate still makes feeding
     * progress instead of stalling at zero forever. A zero (or negative) rate or period drains
     * nothing. The intermediate product is widened to {@code long} and the result clamped into
     * {@code int} range, so a large rate never overflows to a negative amount — the return is never
     * negative.
     *
     * @param perSecond the configured {@code feeding.xp-per-second} rate
     * @param period the feeding tick period in ticks (e.g. {@link #FEED_PERIOD_TICKS})
     * @return points to drain this tick, {@code >= 0}
     */
    public static int drainThisTick(int perSecond, int period) {
        if (perSecond <= 0 || period <= 0) {
            return 0;
        }
        long exact = (long) perSecond * period / 20L;
        if (exact <= 0L) {
            return 1; // documented minimum so a sub-integer rate still progresses
        }
        return exact > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) exact;
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

        // Drain in POINTS, clamped to what the gazer actually holds so they never go below zero.
        int currentPoints = Math.max(0, Xp.totalPointsAt(gazer.getLevel(), gazer.getExp()));
        int wanted = drainThisTick(config.xpPerSecond(), FEED_PERIOD_TICKS);
        int drained = Math.min(wanted, currentPoints);
        if (drained > 0) {
            // Paper's giveExp adjusts level+bar together and is point-accurate for negatives;
            // because drained <= currentPoints, exactly `drained` points are removed. Banked ==
            // removed by construction.
            gazer.giveExp(-drained);
            s.bank(drained);
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

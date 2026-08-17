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

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.xpfarm.box.config.BoxConfig;
import org.xpfarm.box.model.BoxState;
import org.xpfarm.box.model.GazeMath;
import org.xpfarm.box.persistence.BoxCodec;
import org.xpfarm.box.persistence.BoxKeys;
import org.xpfarm.box.persistence.BoxRegistry;

/**
 * Bukkit adapter that creates Box creatures: the scheduled per-player {@link #rollFor(Player)}
 * natural spawn, and the shared {@link #spawnAt(Location, UUID)} used by both natural rolls and
 * {@code /box summon} (Task 15).
 *
 * <p>Per spec §4.6 this is a scheduled roll, <strong>not</strong> a {@code CreatureSpawnEvent}
 * hook: the placement rules ("night, direct sky access, a distance band around the player, outside
 * their view cone, away from world spawn, under the caps") are too specific to filter after the
 * fact. This service does not own the scheduler — it exposes {@link #rollFor(Player)} for the tick
 * wiring in Task 15 (BoxPlugin) to call on each eligible player every
 * {@code spawn.check-interval-seconds}. Installing the plugin is inert because
 * {@code spawn.enabled} ships {@code false} and {@link #rollFor} early-returns when it is off.
 *
 * <h2>The per-player-cap interpretation (spec §4.6, "one per player at most")</h2>
 *
 * The design says at most one Box per player. But a freshly rolled creature is
 * {@link BoxState.Phase#DORMANT} and <strong>unbound</strong> — it has no victim yet — and
 * {@link BoxRegistry#countForVictim(UUID)} counts only <em>bound</em> states. So the honest, cheap
 * reading enforced here is: <em>do not spawn a new creature for a player who is already being hunted
 * by their cap of bound creatures.</em> The gate is
 * {@code withinCaps(registry.countForVictim(p), perPlayerCap, registry.size(), serverCap)}.
 *
 * <p>This deliberately does <strong>not</strong> attribute dormant/unbound creatures to the player
 * they were rolled near, because a dormant creature carries no player association (binding happens
 * later, when a gaze locks on — Task 9). The consequence is a known, bounded looseness: several
 * dormant creatures could accumulate around one player before any of them binds. That looseness is
 * capped globally by {@code serverCap} (via {@link BoxRegistry#size()}), and in practice the low
 * {@code spawn.chance} and the interval between rolls make it rare. Tightening it further would
 * require a separate "rolled-for-player" ledger with its own expiry — extra mutable state the design
 * does not call for — so it is recorded as a concern rather than built. See the task report.
 *
 * <h2>Gate-7a obligations (live-only, not unit-tested here)</h2>
 *
 * The world spawn, the distance-band placement, the sky-access probe, and the view-cone exclusion
 * all require a running server's {@code World}, {@code Player}, and {@code Shulker}, which cannot be
 * faithfully mocked headlessly. They are acceptance checks verified at gate 7a:
 *
 * <ul>
 *   <li><b>Acceptance check 1</b> — with {@code spawn.enabled=true} at night a creature appears
 *       within the distance band, on a sky-lit block, never inside the player's view cone, honoring
 *       both caps and the world-spawn exclusion.
 *   <li><b>Natural-spawn conditions</b> — overworld only, night window, sky access, and world-spawn
 *       distance are observed live.
 * </ul>
 *
 * The unit-testable seams are the pure predicates {@link #isNight(long, int, int)} and
 * {@link #withinCaps(long, int, int, int)}, both covered by {@code SpawnPlacementTest}.
 */
public final class SpawnService {

    /** Candidate horizontal directions the placement search tries, in a stable order. */
    private static final int PLACEMENT_ATTEMPTS = 16;

    private final Plugin plugin;
    private final BoxConfig config;
    private final BoxRegistry registry;
    private final BoxKeys keys;
    private final BoxCodec codec;

    /**
     * @param plugin the owning plugin, used only for its {@link World#spawn} scheduler affinity via
     *     the world handle (no scheduling is started here)
     * @param config the validated configuration supplying the spawn rules and caps
     * @param registry the live-creature index consulted for the caps and updated on spawn
     * @param keys the shared PDC keys (held for parity with the other adapters; the codec owns the
     *     actual writes)
     * @param codec writes the initial {@link BoxState} onto the spawned entity's PDC
     */
    public SpawnService(Plugin plugin, BoxConfig config, BoxRegistry registry, BoxKeys keys,
            BoxCodec codec) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /**
     * Runs one natural-spawn roll for a single eligible player. Called by the Task 15 scheduler on
     * each online player every {@code spawn.check-interval-seconds}; a no-op unless every placement
     * rule passes and the {@code spawn.chance} roll succeeds.
     *
     * <p>Gates, in order (each a cheap early-return before the next):
     * <ol>
     *   <li>{@code spawn.enabled} — off by default, so an untouched install never spawns;
     *   <li>overworld only ({@link World.Environment#NORMAL});
     *   <li>night — {@code isNight(world.getTime(), nightStart, nightEnd)};
     *   <li>caps — {@code withinCaps(countForVictim(player), perPlayerCap, size, serverCap)}
     *       (see the class Javadoc for the per-player-cap interpretation);
     *   <li>{@code spawn.chance} — the probability roll that keeps the creature rare;
     *   <li>a valid placement in the {@code [minDistance, maxDistance]} band that also clears the
     *       world-spawn exclusion, has sky access if required, and sits outside the view cone.
     * </ol>
     *
     * <p>On success it calls {@link #spawnAt(Location, UUID)} with a {@code null} victim: the natural
     * spawn is dormant and unbound. The chance roll is placed <em>before</em> the (more expensive)
     * placement search so most rolls cost almost nothing.
     *
     * <p><strong>Live path (gate 7a).</strong> The world query, placement search, sky probe, and
     * world spawn require a running server and are not unit-tested.
     *
     * @param p the player to roll for (a no-op when {@code null})
     */
    public void rollFor(Player p) {
        if (!config.spawnEnabled() || p == null) {
            return;
        }
        World world = p.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        if (!isNight(world.getTime(), config.nightStart(), config.nightEnd())) {
            return;
        }
        long perVictim = registry.countForVictim(p.getUniqueId());
        if (!withinCaps(perVictim, config.perPlayerCap(), registry.size(), config.serverCap())) {
            return;
        }
        // Roll the dice before the placement search: a failed roll should cost nothing.
        if (ThreadLocalRandom.current().nextDouble() >= config.chance()) {
            return;
        }
        Location spot = findSpawn(p);
        if (spot == null) {
            return;
        }
        spawnAt(spot, null);
    }

    /**
     * The shared spawn used by both natural rolls and {@code /box summon} (Task 15). Spawns a black,
     * silent, AI-less, persistent {@link Shulker} attached facing down, seeds a fresh
     * {@link BoxState} onto its PDC via the codec, binds the victim when one is given, and registers
     * it with the {@link BoxRegistry}.
     *
     * <p>All configured entity flags are server-side and render identically for Bedrock players via
     * Geyser: {@code setAI(false)} (it moves only by our teleport), {@code setSilent(true)} (we drive
     * every sound ourselves), {@code setColor(BLACK)}, {@code setRemoveWhenFarAway(false)} +
     * {@code setPersistent(true)} (never despawned by vanilla), and {@code setAttachedFace(DOWN)}.
     *
     * <p>The creature id is the shulker's own {@link Shulker#getUniqueId()} so the entity and its
     * state share one identity, and {@code spawnedEpochSecond} is the current wall-clock second. When
     * {@code victim} is non-null the state is bound to it (a summon-with-target); a {@code null}
     * victim leaves it dormant and unbound.
     *
     * <p><strong>Live path (gate 7a).</strong> The world spawn and entity configuration require a
     * running server and are not unit-tested.
     *
     * @param loc the world location to spawn at (its world must be loaded)
     * @param victim the victim to bind to, or {@code null} to spawn dormant and unbound
     * @return the spawned, configured, tracked shulker
     */
    public Shulker spawnAt(Location loc, @Nullable UUID victim) {
        Objects.requireNonNull(loc, "loc");
        World world = Objects.requireNonNull(loc.getWorld(), "loc.world");
        Shulker box = world.spawn(loc, Shulker.class, s -> {
            s.setAI(false);
            s.setSilent(true);
            s.setColor(DyeColor.BLACK);
            s.setRemoveWhenFarAway(false);
            s.setPersistent(true);
            s.setAttachedFace(BlockFace.DOWN);
        });

        long now = Instant.now().getEpochSecond();
        BoxState state = new BoxState(box.getUniqueId(), now);
        if (victim != null) {
            state.bindTo(victim, now);
        }
        codec.write(box.getPersistentDataContainer(), state);
        registry.track(box.getUniqueId(), state);
        return box;
    }

    /**
     * Searches for a spawn location satisfying every placement rule: within the
     * {@code [minDistance, maxDistance]} band around the player, at least
     * {@code minDistanceFromWorldSpawn} from world spawn, with direct sky access if configured, and
     * outside the player's current view cone. Tries {@link #PLACEMENT_ATTEMPTS} random points and
     * returns the first that qualifies, or {@code null} when none do this roll (the creature simply
     * does not spawn — the next roll tries again).
     *
     * <p>Each candidate picks a random bearing and a random radius in the band around the player's
     * feet, placing the spawn at the <em>player's elevation</em> at that {@code (x, z)}. Sky access
     * then requires {@code world.getHighestBlockYAt(x, z) <= spawnY}: the highest solid block in the
     * column is at or below the spawn, so nothing overhangs it. Tied to the player's Y this is the
     * "not in caves" rule (spec §4.6) — a player underground sees the surface tower above their Y, so
     * every nearby column fails and no natural spawn occurs down there.
     *
     * <p><strong>Live path (gate 7a).</strong> Reads live world geometry; not unit-tested.
     */
    private @Nullable Location findSpawn(Player p) {
        World world = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector eyePos = eye.toVector();
        Vector look = eye.getDirection();
        Location feet = p.getLocation();
        int spawnY = feet.getBlockY();
        Location worldSpawn = world.getSpawnLocation();
        double min = config.minDistance();
        double max = config.maxDistance();
        double minFromSpawnSq =
                (double) config.minDistanceFromWorldSpawn() * config.minDistanceFromWorldSpawn();

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < PLACEMENT_ATTEMPTS; i++) {
            double angle = rng.nextDouble(0.0, Math.PI * 2.0);
            double radius = min == max ? min : rng.nextDouble(min, max);
            int x = feet.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = feet.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);

            // "Not in caves": the column must be open to the sky at or below the spawn elevation.
            if (config.requireSkyAccess() && world.getHighestBlockYAt(x, z) > spawnY) {
                continue;
            }

            Location candidate = new Location(world, x + 0.5, spawnY, z + 0.5);
            if (worldSpawn.distanceSquared(candidate) < minFromSpawnSq) {
                continue;
            }
            // Reject anything inside the player's current view cone so it never pops in in front.
            if (GazeMath.inCone(eyePos, look, candidate.toVector(), config.fovCosine())) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    /**
     * Whether {@code time} lies within the inclusive night window {@code [start, end]}. The overworld
     * night runs roughly 13000–23000 ticks; both boundaries count as night.
     *
     * <p>Pure predicate, unit-tested. {@code start <= end} is guaranteed by {@link BoxConfig}
     * validation, so a window is never empty.
     *
     * @param time the world time in ticks ({@code world.getTime()})
     * @param start the night-window start tick, inclusive
     * @param end the night-window end tick, inclusive
     * @return {@code true} when {@code start <= time <= end}
     */
    public static boolean isNight(long time, int start, int end) {
        return start <= time && time <= end;
    }

    /**
     * Whether a new creature may spawn under both caps: the player is being hunted by fewer than
     * {@code perPlayerCap} bound creatures <em>and</em> the server holds fewer than {@code serverCap}
     * live creatures. Both comparisons are strict {@code <}, so a count that has reached its cap
     * blocks the spawn.
     *
     * <p>Pure predicate, unit-tested. See the class Javadoc for why {@code perVictimCount} is the
     * bound-creature count for the player rather than an attribution of dormant creatures.
     *
     * @param perVictimCount bound creatures currently hunting this player
     *     ({@link BoxRegistry#countForVictim})
     * @param perPlayerCap the configured per-player cap ({@code >= 1})
     * @param serverSize live creatures server-wide ({@link BoxRegistry#size})
     * @param serverCap the configured server cap ({@code >= 0})
     * @return {@code true} when {@code perVictimCount < perPlayerCap && serverSize < serverCap}
     */
    public static boolean withinCaps(long perVictimCount, int perPlayerCap, int serverSize,
            int serverCap) {
        return perVictimCount < perPlayerCap && serverSize < serverCap;
    }
}

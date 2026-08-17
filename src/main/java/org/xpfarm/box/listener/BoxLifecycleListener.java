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
package org.xpfarm.box.listener;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.xpfarm.box.config.BoxConfig;
import org.xpfarm.box.model.BoxState;
import org.xpfarm.box.persistence.BoxCodec;
import org.xpfarm.box.persistence.BoxRegistry;
import org.xpfarm.box.service.ArtifactSource;
import org.xpfarm.box.service.SoundPlayer;

/**
 * Bukkit {@link Listener} that enforces the creature's whole event-driven lifecycle: the open-only
 * damage window, death (artifact drop + cleanup), suppression of the three vanilla shulker behaviors,
 * state rehydration on chunk load, and dormancy on quit / join / player-death / dimension-change.
 *
 * <p>Almost every handler here touches a running server's entities, worlds, drops, and players, which
 * cannot be faithfully mocked, so they are <strong>gate-7a</strong> obligations documented per method
 * rather than unit-tested. The one pure, unit-testable seam is {@link #vulnerable(double)} — the
 * damage-gate predicate that decides, from a shulker's {@code getPeek()}, whether damage lands. It is
 * covered exhaustively by {@code DamageGateTest} (acceptance check 7).
 *
 * <h2>The vanilla-teleport-vs-our-movement decision (deliberate omission)</h2>
 *
 * The design lists {@code EntityTeleportEvent} as "suppress vanilla shulker self-teleport." This
 * listener <strong>intentionally does not register that handler</strong>, and the reasoning is
 * load-bearing:
 *
 * <ul>
 *   <li>Our {@code MovementService} moves the creature exclusively via {@code shulker.teleport(...)}.
 *       A blanket {@code EntityTeleportEvent} canceller for tracked shulkers would risk cancelling our
 *       own movement — and a creature that cannot move is a total failure.</li>
 *   <li>{@code EntityTeleportEvent} in this API carries <strong>no {@code TeleportCause}</strong> —
 *       only {@code getFrom()}/{@code getTo()}/{@code setCancelled(...)} (verified against
 *       {@code paper-api 26.1.2}). There is therefore no reliable field on the event to distinguish a
 *       vanilla self-teleport from our plugin teleport, so a precise "cancel only vanilla" guard is
 *       not expressible from the event alone.</li>
 *   <li>The vanilla shulker self-teleport is an AI goal, and {@code SpawnService} spawns every
 *       creature with {@code setAI(false)}. Disabling the AI already suppresses the vanilla teleport
 *       at the source, which is exactly acceptance check 1 ("never self-teleports").</li>
 * </ul>
 *
 * The chosen design is thus: rely on {@code setAI(false)} as the single, sufficient suppression and do
 * <em>not</em> add a blanket canceller that could freeze the creature. Confirming no vanilla teleport
 * slips through despite {@code setAI(false)} is a <strong>gate-7a verification obligation</strong>. If
 * one ever does (e.g. a future Paper build), the guarded fix is a transient PDC/metadata flag set
 * around {@code MovementService}'s {@code teleport(...)} and checked in a re-added handler — never a
 * blanket cancel. {@link #onProjectileLaunch} and {@link #onTarget} have no such ambiguity and are
 * suppressed here as belt-and-suspenders over {@code setAI(false)}.
 *
 * <h2>Persistence of in-memory mutations</h2>
 *
 * The dormancy handlers mutate the live {@link BoxState} held in the {@link BoxRegistry} (phase,
 * binding) rather than writing the entity PDC directly, because the victim's {@link Player} — not the
 * creature entity — is what these events carry, and the creature may sit in an unloaded chunk. The
 * tick loop (Task 15) is the single writer that flushes mutated state back to each entity's PDC via
 * the {@link BoxCodec}. {@link #onDeath} is the exception: it holds the dying entity directly, so it
 * untracks immediately.
 */
public final class BoxLifecycleListener implements Listener {

    private final BoxRegistry registry;
    private final BoxCodec codec;
    private final BoxConfig config;
    private final ArtifactSource artifacts;
    private final SoundPlayer sounds;
    private final LongSupplier nowSecond;

    /**
     * Per-creature offline deadline: the epoch second past which a creature whose victim logged out
     * has waited longer than {@code lifetime.offline-dormant-minutes} and must revert to
     * {@code DORMANT}. Populated on {@link #onQuit} and consulted (then cleared) on {@link #onJoin};
     * transient live-session state, never persisted. Concurrent-safe for a possible off-thread reader.
     */
    private final Map<UUID, Long> offlineDeadline = new ConcurrentHashMap<>();

    /**
     * Production constructor: the current epoch second is read from the system clock.
     *
     * @param registry the live-creature index, source of in-memory state and the untrack target
     * @param codec reads {@link BoxState} back from an entity PDC on chunk load
     * @param config supplies {@code lifetime.offline-dormant-minutes} and
     *     {@code lifetime.unbind-on-victim-death}
     * @param artifacts the seam that mints the death-drop artifact from banked XP (Task 14)
     * @param sounds the sound adapter used for the death cue
     */
    public BoxLifecycleListener(BoxRegistry registry, BoxCodec codec, BoxConfig config,
            ArtifactSource artifacts, SoundPlayer sounds) {
        this(registry, codec, config, artifacts, sounds,
                () -> Instant.now().getEpochSecond());
    }

    /**
     * Seam constructor used by tests, which inject a deterministic clock instead of wall time.
     *
     * @param nowSecond supplies the current epoch second
     */
    public BoxLifecycleListener(BoxRegistry registry, BoxCodec codec, BoxConfig config,
            ArtifactSource artifacts, SoundPlayer sounds, LongSupplier nowSecond) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.config = Objects.requireNonNull(config, "config");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.nowSecond = Objects.requireNonNull(nowSecond, "nowSecond");
    }

    /**
     * The damage-gate predicate: a shulker shell is vulnerable exactly when it is open, i.e. its
     * {@code getPeek()} is strictly positive. A sealed shell ({@code peek <= 0}) is the closed-armor
     * window and takes no damage.
     *
     * <p>Pure and side-effect free — the sole unit-testable seam of this listener (acceptance
     * check 7). {@code getPeek()} is never negative in practice; the strict {@code > 0} still reads a
     * defensive negative as sealed.
     *
     * @param peek the shulker's current peek value ({@code 0.0} sealed to {@code 1.0} fully open)
     * @return {@code true} when the shell is open and damage should apply
     */
    public static boolean vulnerable(double peek) {
        return peek > 0.0;
    }

    /**
     * Enforces the open-only vulnerability window: for a tracked creature that is <em>not</em> open
     * ({@link #vulnerable(double)} is {@code false}), the damage is cancelled (the closed shell is
     * armored). An open, feeding shell takes damage normally. Untracked shulkers and non-shulkers are
     * ignored so ordinary mobs are unaffected.
     *
     * <p><strong>Live path (gate 7a) — acceptance check 7.</strong> Reads {@code getPeek()} off a
     * running entity; the decision itself is unit-tested via {@link #vulnerable(double)}.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Shulker box = trackedShulker(event.getEntity());
        if (box == null) {
            return;
        }
        if (!vulnerable(box.getPeek())) {
            event.setCancelled(true);
        }
    }

    /**
     * On the death of a tracked creature: clears the vanilla shulker drops (shell, XP orbs), drops
     * exactly one cursed artifact carrying the creature's banked XP built through the {@link
     * ArtifactSource} seam, plays the death cue, and untracks the creature from the registry so the
     * tick loop stops driving it.
     *
     * <p><strong>Live path (gate 7a) — acceptance check 12.</strong> Depends on a running server's
     * drop list and world.
     */
    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Shulker box = trackedShulker(event.getEntity());
        if (box == null) {
            return;
        }
        UUID id = box.getUniqueId();
        BoxState state = registry.get(id).orElse(null);

        // Exactly one artifact, no vanilla shell/orbs (acceptance check 12).
        event.getDrops().clear();
        if (state != null) {
            ItemStack artifact = artifacts.build(state.bankedXp());
            if (artifact != null) {
                event.getDrops().add(artifact);
            }
        }

        sounds.play(box.getLocation(), "death");
        registry.untrack(id);
        offlineDeadline.remove(id);
    }

    /**
     * Suppresses vanilla shulker bullets for tracked creatures (acceptance check 1): the creature is a
     * silent stalker, not a turret. Belt-and-suspenders over {@code setAI(false)}.
     *
     * <p><strong>Live path (gate 7a) — acceptance check 1.</strong>
     */
    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        // The launched projectile's shooter is the shulker itself; suppress only our creatures' bullets.
        if (event.getEntity().getShooter() instanceof Entity shooter
                && trackedShulker(shooter) != null) {
            event.setCancelled(true);
        }
    }

    /**
     * Suppresses vanilla shulker targeting for tracked creatures (acceptance check 1 / 20): pursuit,
     * contact, and the kill are driven by our services against the bound victim only, never by vanilla
     * AI targeting. Belt-and-suspenders over {@code setAI(false)}.
     *
     * <p><strong>Live path (gate 7a) — acceptance checks 1, 20.</strong>
     */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (trackedShulker(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    /**
     * Rehydrates in-memory state when a chunk's entities load: every shulker carrying our PDC marker
     * is decoded via the {@link BoxCodec} and (re)tracked in the {@link BoxRegistry}, so a creature
     * that survived a chunk unload or a server restart is driven again with its stage, victim, and
     * banked XP intact (acceptance check 13). Re-tracking is idempotent.
     *
     * <p><strong>Live path (gate 7a) — acceptance check 13.</strong>
     */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof Shulker shulker)) {
                continue;
            }
            Optional<BoxState> state = codec.read(shulker.getPersistentDataContainer());
            state.ifPresent(s -> registry.track(shulker.getUniqueId(), s));
        }
    }

    /**
     * Starts the offline-dormancy timer when a victim logs out: every creature bound to them enters
     * {@code WAITING} (stationary, starving) and its re-bind deadline is recorded as {@code now +
     * offline-dormant-minutes}. If the player returns before then {@link #onJoin} resumes the hunt;
     * otherwise the binding is released on their next join.
     *
     * <p><strong>Live path (gate 7a).</strong> Mutates in-memory state; the tick loop persists it.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID victim = event.getPlayer().getUniqueId();
        long deadline = nowSecond.getAsLong() + (long) config.offlineDormantMinutes() * 60L;
        for (BoxState s : registry.all()) {
            if (s.isBound() && victim.equals(s.victim())) {
                s.setPhase(BoxState.Phase.WAITING);
                offlineDeadline.put(s.creatureId(), deadline);
            }
        }
    }

    /**
     * Resolves the offline-dormancy timer when a victim logs back in: a creature bound to them that is
     * still within its deadline resumes {@code HUNTING}; one whose deadline has passed releases the
     * binding and reverts to {@code DORMANT} where it stands (still out there, lockable again). The
     * per-creature deadline is cleared either way.
     *
     * <p><strong>Live path (gate 7a).</strong> Mutates in-memory state; the tick loop persists it.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID victim = event.getPlayer().getUniqueId();
        long now = nowSecond.getAsLong();
        for (BoxState s : registry.all()) {
            if (!s.isBound() || !victim.equals(s.victim())) {
                continue;
            }
            Long deadline = offlineDeadline.remove(s.creatureId());
            if (deadline != null && now > deadline) {
                // Waited too long: release the binding and go dormant in place.
                s.unbind();
                s.setPhase(BoxState.Phase.DORMANT);
            } else {
                // Back in time: resume the hunt.
                s.setPhase(BoxState.Phase.HUNTING);
            }
        }
    }

    /**
     * Releases or retains the binding when a victim dies, per {@code lifetime.unbind-on-victim-death}
     * (acceptance check 21). When {@code true} (default) the creature unbinds and reverts to {@code
     * DORMANT} where it stands — lockable again by anyone, including the player who just died. When
     * {@code false} it keeps the binding and enters {@code WAITING} until that player returns.
     *
     * <p><strong>Live path (gate 7a) — acceptance check 21.</strong> Mutates in-memory state; the
     * tick loop persists it.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID victim = event.getEntity().getUniqueId();
        boolean unbind = config.unbindOnVictimDeath();
        for (BoxState s : registry.all()) {
            if (!s.isBound() || !victim.equals(s.victim())) {
                continue;
            }
            if (unbind) {
                s.unbind();
                s.setPhase(BoxState.Phase.DORMANT);
            } else {
                s.setPhase(BoxState.Phase.WAITING);
            }
            offlineDeadline.remove(s.creatureId());
        }
    }

    /**
     * Handles dormancy on a dimension change: the creature cannot follow a victim through a portal, so
     * every creature bound to that player enters {@code WAITING} while they are away. It keeps the
     * binding; resuming the hunt when the victim re-enters the creature's dimension is the tick loop's
     * concern (it compares the victim's world to the creature's).
     *
     * <p><strong>Live path (gate 7a).</strong> Mutates in-memory state; the tick loop persists it.
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID victim = event.getPlayer().getUniqueId();
        for (BoxState s : registry.all()) {
            if (s.isBound() && victim.equals(s.victim())) {
                s.setPhase(BoxState.Phase.WAITING);
            }
        }
    }

    /**
     * Narrows an entity to a tracked Box creature: the shulker whose id the registry knows. Returns
     * {@code null} for non-shulkers and for shulkers we do not track, so vanilla mobs are never
     * touched by any handler here.
     */
    private Shulker trackedShulker(Entity entity) {
        if (!(entity instanceof Shulker shulker)) {
            return null;
        }
        return registry.get(shulker.getUniqueId()).isPresent() ? shulker : null;
    }
}

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
package org.xpfarm.box;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.event.HandlerList;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import org.xpfarm.box.command.BoxCommand;
import org.xpfarm.box.config.BoxConfig;
import org.xpfarm.box.config.StageDef;
import org.xpfarm.box.listener.BoxLifecycleListener;
import org.xpfarm.box.model.BoxState;
import org.xpfarm.box.model.StageTable;
import org.xpfarm.box.persistence.BoxCodec;
import org.xpfarm.box.persistence.BoxKeys;
import org.xpfarm.box.persistence.BoxRegistry;
import org.xpfarm.box.service.ArtifactService;
import org.xpfarm.box.service.FeedingService;
import org.xpfarm.box.service.GazeService;
import org.xpfarm.box.service.MovementService;
import org.xpfarm.box.service.SoundPlayer;
import org.xpfarm.box.service.SpawnService;

/**
 * Plugin entry point: assembles every component (Task 15 capstone wiring).
 *
 * <p><strong>Enable order</strong> (mirrors the reference {@code RedstoneTrainPlugin}).
 * {@code config.yml} is validated into an immutable {@link BoxConfig} first — an out-of-range scalar
 * falls back to its shipped default (logged as a warning) so one typo'd number never takes the plugin
 * offline (acceptance check 19); only a structurally broken file disables it gracefully rather than
 * leaving a half-wired state. Then the
 * config-independent core is built ({@link BoxKeys}, {@link BoxCodec}, {@link BoxRegistry}),
 * creatures are rehydrated from the PDC of every already-loaded world's shulkers, and finally
 * {@link #wireConfigServices} builds everything that holds the config snapshot ({@link StageTable},
 * {@link SoundPlayer}, {@link GazeService}, {@link FeedingService}, {@link MovementService},
 * {@link SpawnService}, {@link ArtifactService}, the {@link BoxLifecycleListener}), registers the
 * {@code /box} command, and starts the two scheduled tasks: the {@code every-2-tick} creature loop
 * and the {@code spawn.check-interval-seconds} natural-spawn roll.
 *
 * <p><strong>Reload.</strong> Every config-holding service takes {@link BoxConfig} as an immutable
 * constructor argument, so {@code /box reload} re-injects by rebuilding them (cancel the tasks,
 * unregister the listener, swap the snapshot, wire fresh instances). On a validation error the old
 * wiring stays untouched and the message is returned to the command. The registry, codec, and keys
 * are config-free and survive reloads, so no creature state is lost (matching the reference and
 * acceptance check 13's spirit).
 *
 * <h2>The tick loop (spec §4.9, obligation D)</h2>
 *
 * One task every {@link TickLoop#PERIOD} ({@code = 2}) ticks — the cadence {@link
 * FeedingService#FEED_PERIOD_TICKS} assumes — iterating the tracked set only, never a per-player
 * world scan. Per creature: resolve the live shulker (skip when its chunk is unloaded, i.e. the
 * entity does not resolve); compute gazers over online players; if <em>any</em> gazer freeze and run
 * lock-on plus feeding; if <em>none</em> close the shell, step toward the victim, test contact; then
 * update audio and persist the possibly-mutated {@link BoxState} back to the entity PDC (the source
 * of truth). A monotonic {@code iterationCounter} advances by one each iteration; because one
 * iteration is {@code PERIOD} ticks, {@link MovementService#stepIfDue} paces in iteration units so an
 * odd tick-interval cannot mis-fire against it (spec §3.7), while lock-on accrues its streak in
 * server ticks (each iteration = {@code PERIOD} ticks) to match {@code gaze.lock-on-ticks}.
 *
 * <h2>Known limitation — offline-expiry durability (obligation C)</h2>
 *
 * The {@link BoxLifecycleListener}'s offline-dormancy deadline is a transient in-memory map, so a
 * victim who logs out, the server restarts, and they rejoin after the timeout resumes {@code HUNTING}
 * instead of unbinding — the deadline was lost with the map. This is <strong>accepted</strong> as
 * join-time-only enforcement (option i in the task brief): the safer null&rarr;HUNTING default is
 * preferred over adding an {@code offline-since} epoch to {@link BoxState}/{@link BoxCodec} and its
 * own tick-loop enforcement (YAGNI). The window is bounded (one offline timeout, only across a
 * restart) and the creature remains lockable by anyone. See the task report.
 *
 * <h2>Known limitation — the "haunting" cue is configured but not yet scheduled</h2>
 *
 * The {@code audio.haunting} sound (Disc 11, to the bound victim alone on an interval) is present in
 * the config contract but is <strong>not played</strong>: it needs a per-victim interval timer that
 * does not exist in this tick loop. The other cues — {@code dormant-ambience}, {@code lock-on-sting},
 * {@code proximity-pulse}, {@code movement}, {@code feeding}, {@code opening}, {@code death} — are all
 * wired. Scheduling "haunting" is a deferred gate-12/future item; it is left configured so the timer
 * can be added later without a config change.
 *
 * <p>Geyser/Bedrock safety: scheduler, PDC, potion effects (Nausea/Darkness/Blindness), titles,
 * {@code playSound}, and commands are all server-side and render identically for Bedrock players via
 * Geyser. No client packets, no NMS.
 */
public final class BoxPlugin extends JavaPlugin {

    // Config-independent core; built once per enable, survives reloads.
    private @Nullable BoxKeys keys;
    private @Nullable BoxCodec codec;
    private @Nullable BoxRegistry registry;

    // Config snapshot and the services rebuilt from it on every (re)load.
    private @Nullable BoxConfig config;
    private @Nullable StageTable stageTable;
    private @Nullable SoundPlayer sounds;
    private @Nullable GazeService gaze;
    private @Nullable FeedingService feeding;
    private @Nullable MovementService movement;
    private @Nullable SpawnService spawnService;
    private @Nullable ArtifactService artifacts;
    private @Nullable BoxLifecycleListener lifecycleListener;
    private @Nullable BukkitTask tickTask;
    private @Nullable BukkitTask spawnTask;

    @Override
    public void onEnable() {
        // 1. Validated config snapshot. Out-of-range scalars fall back to defaults per key (each
        //    logged as a warning); only a structurally broken config.yml (e.g. a malformed stages
        //    section) disables the plugin gracefully (acceptance check 19).
        saveDefaultConfig();
        BoxConfig.Result loaded;
        try {
            loaded = BoxConfig.fromValidated(getConfig());
        } catch (RuntimeException invalid) {
            getLogger().severe("config.yml is structurally invalid: " + invalid.getMessage());
            getLogger().severe("Fix it (or delete config.yml to regenerate the defaults) and "
                    + "restart. Disabling The Box.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        for (String warning : loaded.warnings()) {
            getLogger().warning(warning);
        }
        config = loaded.config();

        // 2. Config-independent core.
        keys = new BoxKeys(this);
        codec = new BoxCodec(keys);
        registry = new BoxRegistry();

        // 3. Rehydrate creatures from already-loaded worlds; later chunk loads are handled by the
        //    lifecycle listener's EntitiesLoad handler (registered in wireConfigServices).
        int restored = rebuildRegistryFromLoadedWorlds();

        // 4. Config-holding services, the command, and the scheduled tasks.
        wireConfigServices(config);

        PluginCommand command = Objects.requireNonNull(getCommand("box"),
                "plugin.yml must declare the box command");
        BoxCommand executor = new BoxCommand(this, registry,
                () -> Objects.requireNonNull(spawnService),
                () -> Objects.requireNonNull(feeding),
                this::reloadBoxConfig);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getLogger().info("The Box enabled; restored " + restored
                + (restored == 1 ? " creature" : " creatures") + " from loaded chunks.");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        persistAllCreatures();
        getLogger().info("The Box disabled.");
    }

    // ------------------------------------------------------------ config wiring

    /**
     * Builds (or rebuilds, on reload) every service that holds the immutable config snapshot,
     * tearing down the previous generation first: both scheduled tasks are cancelled and the old
     * lifecycle listener is unregistered. The registry, codec, and keys are config-free and are
     * never rebuilt, so no creature state is lost across a reload.
     */
    private void wireConfigServices(BoxConfig fresh) {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (spawnTask != null) {
            spawnTask.cancel();
        }
        if (lifecycleListener != null) {
            HandlerList.unregisterAll(lifecycleListener);
        }

        config = fresh;
        BoxRegistry registry = Objects.requireNonNull(this.registry);
        BoxCodec codec = Objects.requireNonNull(this.codec);
        BoxKeys keys = Objects.requireNonNull(this.keys);

        StageTable stageTable = new StageTable(fresh.stages());
        SoundPlayer sounds = new SoundPlayer(fresh);
        GazeService gaze = new GazeService(fresh);
        FeedingService feeding = new FeedingService(fresh, stageTable, sounds, keys, codec);
        MovementService movement = new MovementService(fresh, sounds);
        SpawnService spawnService = new SpawnService(this, fresh, registry, keys, codec);
        ArtifactService artifacts = new ArtifactService(this, fresh, keys);
        BoxLifecycleListener listener =
                new BoxLifecycleListener(registry, codec, fresh, artifacts, sounds);

        this.stageTable = stageTable;
        this.sounds = sounds;
        this.gaze = gaze;
        this.feeding = feeding;
        this.movement = movement;
        this.spawnService = spawnService;
        this.artifacts = artifacts;
        this.lifecycleListener = listener;

        getServer().getPluginManager().registerEvents(listener, this);

        TickLoop loop = new TickLoop(fresh, stageTable, gaze, feeding, movement, sounds);
        tickTask = getServer().getScheduler().runTaskTimer(this, loop, TickLoop.PERIOD,
                TickLoop.PERIOD);

        long spawnPeriod = Math.max(1L, (long) fresh.checkIntervalSeconds() * 20L);
        spawnTask = getServer().getScheduler().runTaskTimer(this, this::rollNaturalSpawns,
                spawnPeriod, spawnPeriod);
    }

    /**
     * {@code /box reload}: rebuild the snapshot from disk and re-wire.
     *
     * @return {@code null} on success, otherwise the validation error message (the previous
     *     configuration and wiring stay active)
     */
    private @Nullable String reloadBoxConfig() {
        reloadConfig();
        BoxConfig.Result fresh;
        try {
            fresh = BoxConfig.fromValidated(getConfig());
        } catch (RuntimeException invalid) {
            // Only a structural error reaches here; out-of-range scalars are defaulted, not rejected.
            getLogger().warning("Reload rejected: " + invalid.getMessage());
            return invalid.getMessage();
        }
        for (String warning : fresh.warnings()) {
            getLogger().warning(warning);
        }
        wireConfigServices(fresh.config());
        getLogger().info("Configuration reloaded and services re-wired.");
        return null;
    }

    /** The natural-spawn roll: one {@link SpawnService#rollFor(Player)} per online player. */
    private void rollNaturalSpawns() {
        SpawnService spawn = this.spawnService;
        if (spawn == null) {
            return;
        }
        for (Player player : getServer().getOnlinePlayers()) {
            spawn.rollFor(player);
        }
    }

    // -------------------------------------------------------- PDC persistence

    /** Scans every loaded world's shulkers and restores tagged creatures. Idempotent. */
    private int rebuildRegistryFromLoadedWorlds() {
        BoxRegistry registry = this.registry;
        BoxCodec codec = this.codec;
        if (registry == null || codec == null) {
            return 0;
        }
        int restored = 0;
        for (World world : getServer().getWorlds()) {
            for (Shulker shulker : world.getEntitiesByClass(Shulker.class)) {
                if (registry.get(shulker.getUniqueId()).isPresent()) {
                    continue;
                }
                var state = codec.read(shulker.getPersistentDataContainer());
                if (state.isPresent()) {
                    registry.track(shulker.getUniqueId(), state.get());
                    restored++;
                }
            }
        }
        return restored;
    }

    /** onDisable: writes every registered creature's state to its entity PDC via the codec. */
    private void persistAllCreatures() {
        BoxRegistry registry = this.registry;
        BoxCodec codec = this.codec;
        if (registry == null || codec == null) {
            return; // enable bailed out on an invalid config; nothing to persist
        }
        int persisted = 0;
        for (BoxState state : registry.all()) {
            if (getServer().getEntity(state.creatureId()) instanceof Shulker box) {
                codec.write(box.getPersistentDataContainer(), state);
                persisted++;
            }
        }
        getLogger().info("Persisted " + persisted
                + (persisted == 1 ? " creature" : " creatures") + " to PDC.");
    }

    // ------------------------------------------------------------- tick loop

    /**
     * The every-2-tick creature driver (spec §4.9). One instance per wiring generation, holding the
     * config-holding services and its own transient state: the monotonic {@code iterationCounter}
     * (one per {@link #PERIOD} ticks) and the per-unbound-creature continuous-gaze streak used for
     * lock-on binding.
     *
     * <p>Reload builds a fresh {@code TickLoop}, so its {@code iterationCounter} and gaze streaks reset —
     * an in-progress lock restarts, which is acceptable for the rare reload path.
     *
     * <p><strong>Live path (gate 7a).</strong> Everything below drives real entities, players, and
     * world geometry and is verified against a live client (acceptance checks 1, 3, 6, 11, 20, 21 and
     * the full end-to-end). It is deliberately not mocked.
     */
    private final class TickLoop implements Runnable {

        /** The loop period in ticks; {@link FeedingService#FEED_PERIOD_TICKS} assumes this cadence. */
        static final long PERIOD = FeedingService.FEED_PERIOD_TICKS;

        /** Fallback entity tracking range (blocks) when a per-world value is unavailable. */
        private static final double DEFAULT_TRACKING_RANGE = 48.0;

        private final BoxConfig config;
        private final StageTable stageTable;
        private final GazeService gaze;
        private final FeedingService feeding;
        private final MovementService movement;
        private final SoundPlayer sounds;

        /**
         * Loop-iteration count (one iteration per {@link #PERIOD} server ticks); advanced by one each
         * iteration. This is the pacing unit for {@link MovementService#stepIfDue} (obligation D):
         * counting iterations keeps the step cadence correct at the loop's real granularity instead of
         * mis-pacing odd tick-intervals against an even server-tick counter.
         */
        private long iterationCounter;

        /**
         * Per-unbound-creature continuous-gaze streak for lock-on: creature id &rarr; (owning player,
         * unbroken gaze accrual <em>in server ticks</em>). Transient; reconciled against the live set
         * each iteration and cleared when the gaze breaks or the creature binds.
         */
        private final Map<UUID, GazeStreak> streaks = new ConcurrentHashMap<>();

        /**
         * Creatures currently within contact radius of their victim, so the below-Gorged contact
         * event (drain + disorientation, spec §3.8) fires once per <em>approach</em> — on the
         * transition into contact — not every iteration while loitering. Reconciled against the live
         * set each iteration; an id is dropped when the creature leaves contact.
         */
        private final Set<UUID> inContact = ConcurrentHashMap.newKeySet();

        TickLoop(BoxConfig config, StageTable stageTable, GazeService gaze, FeedingService feeding,
                MovementService movement, SoundPlayer sounds) {
            this.config = config;
            this.stageTable = stageTable;
            this.gaze = gaze;
            this.feeding = feeding;
            this.movement = movement;
            this.sounds = sounds;
        }

        @Override
        public void run() {
            BoxRegistry registry = BoxPlugin.this.registry;
            if (registry == null) {
                return;
            }
            long now = Instant.now().getEpochSecond();
            Collection<? extends Player> online = getServer().getOnlinePlayers();
            Set<UUID> liveIds = new HashSet<>();

            for (BoxState state : registry.all()) {
                UUID id = state.creatureId();
                Entity entity = getServer().getEntity(id);
                if (entity == null) {
                    // Unloaded chunk (or not yet resolvable): keep tracked, keep any carry.
                    liveIds.add(id);
                    continue;
                }
                if (!(entity instanceof Shulker box) || box.isDead() || !box.isValid()) {
                    // Gone for good with no death event (e.g. an external /kill-by-removal): untrack.
                    registry.untrack(id);
                    continue;
                }
                liveIds.add(id);
                processCreature(box, state, online, now);
                // The entity PDC is the source of truth: flush the (possibly mutated) state.
                PersistentDataContainer pdc = box.getPersistentDataContainer();
                Objects.requireNonNull(BoxPlugin.this.codec).write(pdc, state);
            }

            // Obligation B: drop any drain-carry (and transient state) for a creature that left.
            feeding.retainOnly(liveIds);
            streaks.keySet().retainAll(liveIds);
            inContact.retainAll(liveIds);
            iterationCounter++;
        }

        /** One creature's full per-tick behavior: gaze &rarr; freeze/feed/lock-on | close/step/contact. */
        private void processCreature(Shulker box, BoxState state,
                Collection<? extends Player> online, long now) {
            World world = box.getWorld();
            double trackingRange = trackingRange(world);
            List<Player> gazers = gaze.gazers(box, online, trackingRange);

            if (!gazers.isEmpty()) {
                // Observed: FROZEN (do not step). Run lock-on and, for a qualifying gazer, feed.
                state.setPhase(BoxState.Phase.FROZEN);
                runLockOn(box, state, gazers, now);
                Player feeder = qualifyingGazer(box, state, gazers);
                if (feeder != null) {
                    state.setPhase(BoxState.Phase.FEEDING);
                    state.setLastFedEpochSecond(now);
                    feeding.feedTick(box, state, feeder, iterationCounter);
                } else {
                    feeding.close(box);
                }
                return;
            }

            // Unobserved: gaze broke, so no lock accrues. Close, then step/contact if bound.
            streaks.remove(box.getUniqueId());
            feeding.close(box);

            if (!state.isBound()) {
                state.setPhase(BoxState.Phase.DORMANT);
                updateAudio(box, state, null);
                return;
            }

            Player victim = getServer().getPlayer(Objects.requireNonNull(state.victim()));
            if (victim == null || !victim.getWorld().equals(world)) {
                // Victim offline or in another dimension: wait where it stands.
                state.setPhase(BoxState.Phase.WAITING);
                updateAudio(box, state, null);
                return;
            }

            Location victimLoc = victim.getLocation();
            // Spec §3.4 / check 16: a sealed victim is unreachable — attach outside and WAIT
            // (quiet), not HUNT (closing-in pulse). stepIfDue is already a no-op when sealed; this
            // fixes the STATE and AUDIO to match the spec.
            if (movement.isSealedFrom(box, victimLoc)) {
                state.setPhase(BoxState.Phase.WAITING);
                updateAudio(box, state, null);
                return;
            }

            // Obligation E: victim is present and reachable in this world — resume the hunt.
            state.setPhase(BoxState.Phase.HUNTING);
            movement.stepIfDue(box, state, victimLoc, now, iterationCounter);
            testContact(box, state, victim, now);
            updateAudio(box, state, victim);
        }

        /**
         * Lock-on (spec §3.2, obligation A + box.exempt): accrue one unbound creature's continuous
         * gaze by a single owning player and, at {@code lock-on-ticks}, bind — but only when the
         * per-player cap still has room and the player is not {@code box.exempt}. Freezing is physics
         * and happens for every gazer; binding is targeting and is gated here.
         */
        private void runLockOn(Shulker box, BoxState state, List<Player> gazers, long now) {
            UUID id = box.getUniqueId();
            if (state.isBound()) {
                streaks.remove(id);
                return;
            }
            // Only a non-exempt gazer may ever own the streak (box.exempt can freeze, never bind).
            List<Player> bindable = new java.util.ArrayList<>();
            for (Player g : gazers) {
                if (!g.hasPermission("box.exempt")) {
                    bindable.add(g);
                }
            }
            if (bindable.isEmpty()) {
                streaks.remove(id);
                return;
            }

            GazeStreak prev = streaks.get(id);
            Player owner = null;
            if (prev != null) {
                for (Player g : bindable) {
                    if (g.getUniqueId().equals(prev.owner)) {
                        owner = g;
                        break;
                    }
                }
            }
            int ticks;
            if (owner == null) {
                owner = bindable.get(0); // gaze switched owner: restart the streak
                ticks = BoxCommand.accrueGaze(0, PERIOD);
            } else {
                // Accrue in SERVER TICKS (each iteration is PERIOD ticks); lock-on-ticks is in ticks.
                ticks = BoxCommand.accrueGaze(prev.ticks, PERIOD);
            }
            streaks.put(id, new GazeStreak(owner.getUniqueId(), ticks));

            if (!BoxCommand.shouldBind(ticks, config.lockOnTicks())) {
                return;
            }
            // Obligation A: the real "one active creature per player" guarantee lives here at bind
            // time — the spawn gate only pre-filters dormant creatures.
            BoxRegistry registry = Objects.requireNonNull(BoxPlugin.this.registry);
            if (registry.countForVictim(owner.getUniqueId()) >= config.perPlayerCap()) {
                streaks.remove(id);
                return;
            }
            bind(box, state, owner, now);
            streaks.remove(id);
        }

        /** Applies the one-time bind: victim, disorientation, sting, whispered title. */
        private void bind(Shulker box, BoxState state, Player owner, long now) {
            state.bindTo(owner.getUniqueId(), now);
            state.setPhase(BoxState.Phase.HUNTING);
            if (config.disorientationEnabled()) {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,
                        config.nauseaTicks(), 0, false, false, true));
                owner.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,
                        config.darknessTicks(), 0, false, false, true));
            }
            sounds.play(box.getLocation(), "lock-on-sting");
            owner.showTitle(Title.title(
                    Component.text("", NamedTextColor.DARK_GRAY),
                    Component.text("It sees you.", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, true)));
        }

        /**
         * The first gazer within the current stage's feed radius that the creature will open for
         * (per {@link FeedingService#shouldOpen}), or {@code null} when none qualifies.
         */
        private @Nullable Player qualifyingGazer(Shulker box, BoxState state, List<Player> gazers) {
            double feedRadius = stageFor(state).feedRadius();
            double feedSq = feedRadius * feedRadius;
            Location boxLoc = box.getLocation();
            for (Player g : gazers) {
                if (!g.getWorld().equals(boxLoc.getWorld())) {
                    continue;
                }
                if (boxLoc.distanceSquared(g.getLocation()) <= feedSq
                        && feeding.shouldOpen(state, g)) {
                    return g;
                }
            }
            return null;
        }

        /**
         * Contact resolution (spec §3.8, acceptance check 11): only against the bound victim, only
         * while unfrozen (this branch). At the Gorged stage ({@code kills-on-contact}) the victim is
         * killed; below Gorged the creature drains everything the victim has and applies heavy
         * disorientation (survivable — it walks away much stronger).
         */
        private void testContact(Shulker box, BoxState state, Player victim, long now) {
            UUID id = box.getUniqueId();
            double radius = config.contactRadius();
            if (box.getLocation().distanceSquared(victim.getLocation()) > radius * radius) {
                inContact.remove(id); // left contact: a later re-approach fires the event again
                return;
            }
            if (stageFor(state).killsOnContact()) {
                victim.setHealth(0.0);
                return;
            }
            // Below Gorged: a discrete "drains everything then walks away" event (spec §3.8), applied
            // once per approach — only on the transition INTO contact, never refreshed while loitering.
            if (!inContact.add(id)) {
                return; // already in contact this approach; the drain already self-limited to empty
            }
            int points = org.xpfarm.box.model.Xp.totalPointsAt(victim.getLevel(), victim.getExp());
            if (points > 0) {
                victim.giveExp(-points);
                state.bank(points);
                state.setLastFedEpochSecond(now);
            }
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                    config.contactBlindnessTicks(), 0, false, false, true));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,
                    config.contactNauseaTicks(), 0, false, false, true));
        }

        /**
         * Audio pulse: the warden-heartbeat proximity cue to the bound victim, quickening as the
         * creature closes; a faint dormant ambience otherwise. Cosmetic, cadence-throttled.
         */
        private void updateAudio(Shulker box, BoxState state, @Nullable Player victim) {
            if (victim != null && state.phase() == BoxState.Phase.HUNTING) {
                double dist = box.getLocation().distance(victim.getLocation());
                long every = Math.max(1L, Math.round(dist / 6.0)); // closer -> more frequent
                if (iterationCounter % every == 0L) {
                    sounds.playTo(victim, "proximity-pulse");
                }
            } else if ((state.phase() == BoxState.Phase.DORMANT
                    || state.phase() == BoxState.Phase.WAITING) && iterationCounter % 40L == 0L) {
                // Quiet ambience for dormant AND waiting (e.g. outside a sealed volume) — never the
                // closing-in pulse, which is reserved for an active, reachable hunt.
                sounds.play(box.getLocation(), "dormant-ambience");
            }
        }

        /**
         * The creature's current stage definition, derived from its banked XP so a just-crossed
         * threshold is reflected immediately (feed radius and kills-on-contact both read this).
         */
        private StageDef stageFor(BoxState state) {
            return stageTable.stageFor(state.bankedXp());
        }

        /**
         * The world's entity tracking range in blocks, defended to {@link #DEFAULT_TRACKING_RANGE}
         * (~48, the vanilla monster tracking distance). No stable public per-world getter exists in
         * this API, so the vanilla default is used; refining to the server's configured monster
         * tracking range is a gate-7a tuning refinement. The {@code world} parameter is retained for
         * that future per-world lookup.
         */
        private double trackingRange(World world) {
            return world == null ? DEFAULT_TRACKING_RANGE : DEFAULT_TRACKING_RANGE;
        }
    }

    /** An unbound creature's continuous-gaze accrual: the owning player and the unbroken count. */
    private record GazeStreak(UUID owner, int ticks) {
    }
}

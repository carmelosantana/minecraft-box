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
package org.xpfarm.box.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xpfarm.box.model.BoxState;
import org.xpfarm.box.persistence.BoxRegistry;
import org.xpfarm.box.service.FeedingService;
import org.xpfarm.box.service.SpawnService;

/**
 * The {@code /box} (alias {@code /thebox}) admin command surface, every subcommand gated on
 * {@code box.admin}.
 *
 * <ul>
 *   <li>{@code summon [player]} — spawns one dormant creature near the target player (or the
 *       sender when omitted) via {@link SpawnService#spawnAt(Location, UUID)}. Acceptance
 *       check 1.</li>
 *   <li>{@code purge <player|all>} — untracks and removes tracked creatures: {@code all}, or only
 *       those bound to the named player. Each removed creature is first sealed via
 *       {@link FeedingService#close(Shulker)} so its transient drain-carry entry cannot leak
 *       (obligation B), then removed from the world and untracked.</li>
 *   <li>{@code list} — prints id, stage, victim, and location for every tracked creature.</li>
 *   <li>{@code reload} — delegates to the injected {@link ConfigReloader}; the plugin rebuilds
 *       {@link org.xpfarm.box.config.BoxConfig} from disk and re-wires every config-holding
 *       service. On a validation error the message is returned and the previous wiring stays
 *       active.</li>
 * </ul>
 *
 * <p>The config-holding services arrive through {@link Supplier}s so a successful reload is visible
 * here without re-registering the executor (the reference {@code RedstoneTrainCommand} idiom). The
 * registry survives reloads and is injected directly.
 *
 * <h2>Unit-testable seams</h2>
 *
 * {@link #parse(String[])} (argument routing to an {@link Action}) and {@link #shouldBind(int, int)}
 * (the lock-on threshold, consumed by the tick loop in {@code BoxPlugin}) are pure and covered by
 * {@code BoxCommandTest}. The live execution paths below touch a running server's entities and are
 * gate-7a obligations documented per method, never mocked.
 *
 * <p>Geyser/Bedrock safety: plain chat components and server-side spawn/removal only — no GUI forms,
 * no client packets.
 */
public final class BoxCommand implements CommandExecutor, TabCompleter {

    /** The parsed subcommand; {@link #UNKNOWN} covers an empty or unrecognized first argument. */
    public enum Action {
        SUMMON,
        PURGE,
        LIST,
        RELOAD,
        UNKNOWN
    }

    /** How the config is rebuilt on {@code /box reload}. */
    @FunctionalInterface
    public interface ConfigReloader {
        /**
         * Rebuilds the config from disk and re-injects it into every config-holding service.
         *
         * @return {@code null} on success, otherwise the validation error message
         */
        @Nullable String reload();
    }

    static final String USAGE =
            "Usage: /box <summon [player] | purge <player|all> | list | reload>";

    private static final int SUMMON_OFFSET_BLOCKS = 3;

    private final Plugin plugin;
    private final BoxRegistry registry;
    private final Supplier<SpawnService> spawn;
    private final Supplier<FeedingService> feeding;
    private final ConfigReloader reloader;

    /**
     * @param plugin the owning plugin, for server/entity access
     * @param registry the live-creature index (survives reloads)
     * @param spawn supplies the current {@link SpawnService} (rebuilt on reload)
     * @param feeding supplies the current {@link FeedingService} (rebuilt on reload), used to seal a
     *     creature before purge so its drain-carry entry is dropped
     * @param reloader rebuilds the config snapshot and re-wires services on {@code reload}
     */
    public BoxCommand(Plugin plugin, BoxRegistry registry, Supplier<SpawnService> spawn,
            Supplier<FeedingService> feeding, ConfigReloader reloader) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.spawn = Objects.requireNonNull(spawn, "spawn");
        this.feeding = Objects.requireNonNull(feeding, "feeding");
        this.reloader = Objects.requireNonNull(reloader, "reloader");
    }

    /**
     * Pure argument router (headless-testable seam): maps the first argument, case-insensitively, to
     * an {@link Action}. An empty argument list or an unrecognized subcommand is {@link
     * Action#UNKNOWN}.
     *
     * @param args the raw command arguments
     * @return the parsed action
     */
    public static Action parse(String[] args) {
        if (args.length == 0) {
            return Action.UNKNOWN;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "summon" -> Action.SUMMON;
            case "purge" -> Action.PURGE;
            case "list" -> Action.LIST;
            case "reload" -> Action.RELOAD;
            default -> Action.UNKNOWN;
        };
    }

    /**
     * The lock-on threshold predicate (headless-testable seam, consumed by the {@code BoxPlugin}
     * tick loop): whether a creature held in one player's continuous gaze for {@code
     * continuousGazeTicks} loop iterations should now bind. Binds once the count reaches {@code
     * lockOnTicks}; a {@code lockOnTicks} of {@code 0} binds on the first gaze tick.
     *
     * @param continuousGazeTicks unbroken gaze loop-iterations the owning player has accrued
     * @param lockOnTicks the configured {@code gaze.lock-on-ticks} threshold
     * @return {@code true} when the creature should bind this tick
     */
    public static boolean shouldBind(int continuousGazeTicks, int lockOnTicks) {
        return continuousGazeTicks >= lockOnTicks;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("box.admin")) {
            sender.sendMessage(Component.text(
                    "You do not have permission to use The Box.", NamedTextColor.RED));
            return true;
        }
        switch (parse(args)) {
            case SUMMON -> summon(sender, args);
            case PURGE -> purge(sender, args);
            case LIST -> list(sender);
            case RELOAD -> reload(sender);
            case UNKNOWN -> sender.sendMessage(Component.text(USAGE, NamedTextColor.GRAY));
            default -> sender.sendMessage(Component.text(USAGE, NamedTextColor.GRAY));
        }
        return true;
    }

    // ----------------------------------------------------------------- summon

    /**
     * Spawns one dormant creature near the target player (argument, else the sender). Live-server
     * glue, gate 7a (acceptance check 1).
     */
    private void summon(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args);
        if (target == null) {
            return;
        }
        Location where = summonLocation(target);
        Shulker box = spawn.get().spawnAt(where, null);
        sender.sendMessage(Component.text("Summoned The Box near " + target.getName() + " ("
                + box.getUniqueId() + ").", NamedTextColor.GREEN));
    }

    /**
     * Resolves the summon target: the named online player if a second argument is present, otherwise
     * the sender when they are a player. Messages and returns {@code null} on a miss.
     */
    private @Nullable Player resolveTarget(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            Player named = plugin.getServer().getPlayerExact(args[1]);
            if (named == null) {
                sender.sendMessage(Component.text("Player '" + args[1] + "' is not online.",
                        NamedTextColor.RED));
            }
            return named;
        }
        if (sender instanceof Player self) {
            return self;
        }
        sender.sendMessage(Component.text(
                "Console must name a target: /box summon <player>.", NamedTextColor.GRAY));
        return null;
    }

    /**
     * A spot a few blocks in front of the player at their elevation. Live-server glue, gate 7a.
     */
    private static Location summonLocation(Player target) {
        Location base = target.getLocation();
        org.bukkit.util.Vector facing = base.getDirection().setY(0.0);
        if (facing.lengthSquared() > 1.0e-6) {
            facing.normalize().multiply(SUMMON_OFFSET_BLOCKS);
            base = base.clone().add(facing);
        }
        return base;
    }

    // ------------------------------------------------------------------ purge

    /**
     * Removes tracked creatures: {@code all}, or only those bound to the named player. Each is sealed
     * via {@link FeedingService#close(Shulker)} (dropping its drain-carry entry, obligation B),
     * removed from the world if resolvable, and untracked. Live-server glue, gate 7a.
     */
    private void purge(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /box purge <player|all>", NamedTextColor.GRAY));
            return;
        }
        boolean all = args[1].equalsIgnoreCase("all");
        UUID victimFilter = null;
        if (!all) {
            Player victim = plugin.getServer().getPlayerExact(args[1]);
            if (victim == null) {
                sender.sendMessage(Component.text("Player '" + args[1]
                        + "' is not online; use 'all' or an online player's name.",
                        NamedTextColor.RED));
                return;
            }
            victimFilter = victim.getUniqueId();
        }

        int removed = 0;
        FeedingService feed = feeding.get();
        for (BoxState state : registry.all()) {
            if (!all && (!state.isBound() || !Objects.equals(state.victim(), victimFilter))) {
                continue;
            }
            UUID id = state.creatureId();
            if (plugin.getServer().getEntity(id) instanceof Shulker box) {
                feed.close(box);
                box.remove();
            } else {
                // Entity in an unloaded chunk (or already gone): drop any stranded carry by id.
                feed.forget(id);
            }
            registry.untrack(id);
            removed++;
        }
        sender.sendMessage(Component.text("Purged " + removed
                + (removed == 1 ? " creature." : " creatures."), NamedTextColor.GREEN));
    }

    // ------------------------------------------------------------------- list

    /** Prints id/stage/victim/location per tracked creature. Live-server glue, gate 7a. */
    private void list(CommandSender sender) {
        var states = registry.all();
        if (states.isEmpty()) {
            sender.sendMessage(Component.text("No Box creatures are tracked.", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("Tracked Box creatures (" + states.size() + "):",
                NamedTextColor.GOLD));
        for (BoxState state : states) {
            sender.sendMessage(Component.text(describe(state), NamedTextColor.WHITE));
        }
    }

    /** One human-readable status line for a creature. */
    private String describe(BoxState state) {
        UUID id = state.creatureId();
        String victim = state.isBound() ? victimName(state.victim()) : "unbound";
        String where = "unloaded";
        if (plugin.getServer().getEntity(id) instanceof Entity entity) {
            Location loc = entity.getLocation();
            where = loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + ","
                    + loc.getBlockZ();
        }
        return "- " + id + " | " + state.phase() + " | stage " + (state.stageIndex() + 1)
                + " | xp " + state.bankedXp() + " | victim " + victim + " | " + where;
    }

    /** A victim UUID rendered as a name when the player is online, else the raw id. */
    private String victimName(@Nullable UUID victim) {
        if (victim == null) {
            return "unbound";
        }
        Player online = plugin.getServer().getPlayer(victim);
        return online != null ? online.getName() : victim.toString();
    }

    // ----------------------------------------------------------------- reload

    private void reload(CommandSender sender) {
        String error = reloader.reload();
        if (error == null) {
            sender.sendMessage(Component.text(
                    "The Box configuration reloaded.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Reload failed: ", NamedTextColor.RED)
                    .append(Component.text(error, NamedTextColor.WHITE))
                    .append(Component.text(
                            " — keeping the previous configuration.", NamedTextColor.RED)));
        }
    }

    // --------------------------------------------------------- tab completion

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
            @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("box.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return completeSubcommand(args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("summon")
                || args[0].equalsIgnoreCase("purge"))) {
            List<String> names = completeOnlinePlayers(sender, args[1]);
            if (args[0].equalsIgnoreCase("purge") && "all".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                names.add(0, "all");
            }
            return names;
        }
        return List.of();
    }

    /** Pure completion: the four subcommands matching the prefix, case-insensitive. */
    static List<String> completeSubcommand(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>(4);
        for (String sub : new String[] {"summon", "purge", "list", "reload"}) {
            if (sub.startsWith(lower)) {
                matches.add(sub);
            }
        }
        return matches;
    }

    /** Online player names matching the prefix. Live-server glue, gate 7a. */
    private List<String> completeOnlinePlayers(CommandSender sender, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(online.getName());
            }
        }
        return matches;
    }
}

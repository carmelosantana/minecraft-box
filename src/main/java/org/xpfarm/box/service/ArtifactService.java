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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.xpfarm.box.config.BoxConfig;
import org.xpfarm.box.model.Xp;
import org.xpfarm.box.persistence.BoxKeys;

/**
 * Bukkit adapter that mints, identifies, and redeems the single cursed artifact a creature drops on
 * death. It builds an item of the configured {@link org.bukkit.Material} whose PDC carries the
 * creature's banked experience, reads that XP back, and — on consumption — returns a configured
 * share of it to the player <em>in points</em> and, only when TheCurse is installed and enabled,
 * asks that plugin to start a curse via a plain console command.
 *
 * <p>This is the concrete implementation of the {@link ArtifactSource} seam that Task 13's
 * {@code BoxLifecycleListener} already depends on; {@link #build(long)} satisfies that interface.
 *
 * <h2>Reflection-free TheCurse soft-link</h2>
 *
 * The integration is a one-way console-command dispatch and nothing more: there is no import of any
 * TheCurse type, no reflection into its internals, and no compile-time dependency on it. When
 * TheCurse is absent the plugin works fully — the artifact still returns XP — and the curse branch
 * is simply skipped (acceptance check 18). The whole decision is extracted to the pure, unit-tested
 * {@link #shouldStartCurse(boolean, boolean)} predicate so the guard can be proven headlessly.
 *
 * <h2>Experience is returned in points, never levels</h2>
 *
 * {@link #consume(org.bukkit.entity.Player, ItemStack)} grants {@link Xp#returnedPoints(long,
 * double)} of the banked total through {@link org.bukkit.entity.Player#giveExp(int)} — the same
 * point-accurate path {@link FeedingService} uses to drain — never {@code setLevel}. Identity is
 * carried by a PDC marker byte, never by display name, so Bedrock players joining through Geyser see
 * a plain vanilla model but every identity check reads the tag.
 *
 * <h2>Gate-7a obligations</h2>
 *
 * {@link #build(long)}, {@link #isArtifact(ItemStack)}, and {@link #consume(org.bukkit.entity.Player,
 * ItemStack)} construct and read {@link ItemStack}/{@link ItemMeta} through {@code
 * Bukkit.getItemFactory()}, grant XP, and dispatch a console command — none of which is available
 * headlessly. They are verified against a live client as gate-7a obligations (acceptance checks 12
 * and 18). Only {@link #shouldStartCurse(boolean, boolean)} is unit-tested.
 */
public final class ArtifactService implements ArtifactSource {

    private final Plugin plugin;
    private final BoxConfig config;
    private final BoxKeys keys;

    /**
     * @param plugin the owning plugin (retained for lifecycle symmetry with the other adapters)
     * @param config the validated configuration snapshot
     * @param keys the shared PDC key registry
     */
    public ArtifactService(Plugin plugin, BoxConfig config, BoxKeys keys) {
        this.plugin = plugin;
        this.config = config;
        this.keys = keys;
    }

    /**
     * Builds the artifact item for a creature that banked {@code bankedXp} points over its life: an
     * item of {@link BoxConfig#artifactMaterial()} named {@link BoxConfig#artifactName()}, tagged
     * with the marker byte and carrying {@code bankedXp} as a LONG in its PDC.
     *
     * <p>Gate-7a: needs {@code Bukkit.getItemFactory()} for meta, unavailable headlessly.
     *
     * @param bankedXp the creature's banked experience points at death, {@code >= 0}
     * @return a fresh artifact {@link ItemStack} carrying that XP in its PDC
     */
    @Override
    public ItemStack build(long bankedXp) {
        ItemStack stack = ItemStack.of(config.artifactMaterial());
        stack.editMeta(meta -> {
            meta.displayName(Component.text(config.artifactName())
                    .decoration(TextDecoration.ITALIC, false));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keys.artifactMarker, PersistentDataType.BYTE, (byte) 1);
            pdc.set(keys.artifactXp, PersistentDataType.LONG, Math.max(0L, bankedXp));
        });
        return stack;
    }

    /**
     * True when {@code it} is a Box artifact: non-null, non-air, with meta whose PDC carries the
     * marker byte. Null/empty-safe.
     *
     * <p>Gate-7a: reads {@link ItemMeta}, unavailable headlessly.
     *
     * @param it the stack to test, may be {@code null}
     * @return whether the stack is one of our artifacts
     */
    public boolean isArtifact(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = it.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(keys.artifactMarker, PersistentDataType.BYTE);
    }

    /**
     * Redeems an artifact: reads its banked XP, grants {@link Xp#returnedPoints(long, double)} of it
     * to {@code p} in points, decrements the stack by one, and — only when
     * {@link #shouldStartCurse(boolean, boolean)} holds — dispatches {@code curse start <name>} from
     * the console so TheCurse (if installed) begins a curse. No-op when {@code it} is not an artifact.
     *
     * <p>Gate-7a: reads {@link ItemMeta}, calls {@code giveExp}, and dispatches a console command,
     * none available headlessly.
     *
     * @param p the redeeming player
     * @param it the artifact stack being consumed
     */
    public void consume(org.bukkit.entity.Player p, ItemStack it) {
        if (!isArtifact(it)) {
            return;
        }
        ItemMeta meta = it.getItemMeta();
        long banked = meta.getPersistentDataContainer()
                .getOrDefault(keys.artifactXp, PersistentDataType.LONG, 0L);
        long points = Xp.returnedPoints(banked, config.xpReturnRatio());
        // giveExp takes an int; clamp the (already ratio-reduced, non-negative) point total.
        int grant = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, points));
        if (grant > 0) {
            p.giveExp(grant);
        }

        it.setAmount(it.getAmount() - 1);

        if (shouldStartCurse(config.curseIntegration(),
                Bukkit.getPluginManager().isPluginEnabled("TheCurse"))) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "curse start " + p.getName());
        }
    }

    /**
     * The console-dispatch guard, the headless-testable seam: a curse starts only when the operator
     * enabled the integration <em>and</em> TheCurse is actually installed and enabled. This is the
     * whole reflection-free soft-link decision — absent TheCurse, {@code cursePluginEnabled} is
     * {@code false} and the artifact simply returns XP (acceptance check 18).
     *
     * @param curseIntegrationConfigured {@link BoxConfig#curseIntegration()}
     * @param cursePluginEnabled whether {@code Bukkit.getPluginManager().isPluginEnabled("TheCurse")}
     * @return {@code curseIntegrationConfigured && cursePluginEnabled}
     */
    static boolean shouldStartCurse(boolean curseIntegrationConfigured, boolean cursePluginEnabled) {
        return curseIntegrationConfigured && cursePluginEnabled;
    }
}

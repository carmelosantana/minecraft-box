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

import org.bukkit.inventory.ItemStack;

/**
 * Seam that builds the single cursed artifact a creature drops on death, carrying its banked
 * experience.
 *
 * <p>This one-method interface exists to break a dependency-order cycle: {@link
 * org.xpfarm.box.listener.BoxLifecycleListener} (Task 13) must drop the artifact on death, but the
 * concrete {@code ArtifactService} that mints it is Task 14, built afterwards. Depending on this
 * narrow abstraction rather than the concrete service lets the listener compile and be reasoned about
 * now; Task 14 supplies the real implementation (and Task 15 wires it in) without any change here.
 *
 * <p>The returned stack must be a fresh, self-contained item whose PDC already carries the banked XP
 * (via {@link org.xpfarm.box.persistence.BoxKeys#artifactXp} and {@link
 * org.xpfarm.box.persistence.BoxKeys#artifactMarker}); the listener drops it verbatim and does not
 * inspect or mutate it. It is a plain {@link java.util.function.LongFunction} in spirit, named as an
 * interface so the collaborator role reads clearly at the wiring site.
 */
@FunctionalInterface
public interface ArtifactSource {

    /**
     * Builds the artifact item for a creature that banked {@code bankedXp} points over its life.
     *
     * @param bankedXp the creature's banked experience points at death, {@code >= 0}
     * @return a fresh artifact {@link ItemStack} carrying that XP in its PDC
     */
    ItemStack build(long bankedXp);
}

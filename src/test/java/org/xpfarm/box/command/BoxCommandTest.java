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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.xpfarm.box.command.BoxCommand.Action;

/**
 * Unit tests for the two pure, headless-testable seams of {@link BoxCommand}: the argument
 * router {@link BoxCommand#parse(String[])} and the lock-on threshold predicate
 * {@link BoxCommand#shouldBind(int, int)}.
 *
 * <p>The live command execution — {@code summon} (world spawn), {@code purge} (entity removal),
 * {@code list} (entity resolution), and {@code reload} (service re-wiring) — depends on a running
 * server's {@code Server}, {@code World}, {@code Player}, and {@code Shulker} and is a gate-7a
 * obligation (acceptance checks 1, 6, 11, 20, 21 and the full end-to-end). It is documented in the
 * {@link BoxCommand} Javadoc, not mocked here.
 */
class BoxCommandTest {

    @Nested
    class Parse {

        @Test
        void emptyArgsIsUnknown() {
            assertEquals(Action.UNKNOWN, BoxCommand.parse(new String[] {}));
        }

        @Test
        void summonRoutes() {
            assertEquals(Action.SUMMON, BoxCommand.parse(new String[] {"summon"}));
        }

        @Test
        void summonWithPlayerArgStillRoutesToSummon() {
            assertEquals(Action.SUMMON, BoxCommand.parse(new String[] {"summon", "Steve"}));
        }

        @Test
        void purgeRoutes() {
            assertEquals(Action.PURGE, BoxCommand.parse(new String[] {"purge", "all"}));
        }

        @Test
        void listRoutes() {
            assertEquals(Action.LIST, BoxCommand.parse(new String[] {"list"}));
        }

        @Test
        void reloadRoutes() {
            assertEquals(Action.RELOAD, BoxCommand.parse(new String[] {"reload"}));
        }

        @Test
        void unknownSubcommandIsUnknown() {
            assertEquals(Action.UNKNOWN, BoxCommand.parse(new String[] {"frobnicate"}));
        }

        @Test
        void routingIsCaseInsensitive() {
            assertEquals(Action.SUMMON, BoxCommand.parse(new String[] {"SuMMoN"}));
            assertEquals(Action.PURGE, BoxCommand.parse(new String[] {"PURGE", "all"}));
            assertEquals(Action.LIST, BoxCommand.parse(new String[] {"List"}));
            assertEquals(Action.RELOAD, BoxCommand.parse(new String[] {"reLOAD"}));
        }
    }

    @Nested
    class ShouldBind {

        // shouldBind(continuousGazeTicks, lockOnTicks) == continuousGazeTicks >= lockOnTicks

        @Test
        void belowThresholdDoesNotBind() {
            assertFalse(BoxCommand.shouldBind(9, 10));
            assertFalse(BoxCommand.shouldBind(0, 10));
        }

        @Test
        void atThresholdBinds() {
            assertTrue(BoxCommand.shouldBind(10, 10));
        }

        @Test
        void aboveThresholdBinds() {
            assertTrue(BoxCommand.shouldBind(11, 10));
        }

        @Test
        void zeroThresholdBindsOnFirstGazeTick() {
            // lock-on-ticks == 0 means bind as soon as a gaze is registered.
            assertTrue(BoxCommand.shouldBind(1, 0));
            assertTrue(BoxCommand.shouldBind(0, 0));
        }

        @Test
        void oneTickThresholdNeedsOneTick() {
            assertFalse(BoxCommand.shouldBind(0, 1));
            assertTrue(BoxCommand.shouldBind(1, 1));
        }
    }
}

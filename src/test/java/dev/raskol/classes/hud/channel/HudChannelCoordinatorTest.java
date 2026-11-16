// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.hud.snapshot.HudFailureReporter;
import dev.raskol.classes.hud.snapshot.PlayerHudSnapshot;
import dev.raskol.classes.hud.snapshot.WidgetFrame;

class HudChannelCoordinatorTest {

    @Test
    void highestPriorityActionBarWinsAndStableIdBreaksTies() {
        Harness harness = new Harness();
        UUID playerId = UUID.randomUUID();

        harness.coordinator.publish(
                playerId,
                snapshot(playerId),
                List.of(
                        frame("zeta", "ACTION_BAR", 50, "Z", OptionalLong.empty()),
                        frame("alpha", "ACTION_BAR", 50, "A", OptionalLong.empty()),
                        frame("low", "ACTION_BAR", 10, "L", OptionalLong.empty())));

        assertEquals("A", harness.output.actionBar);
    }

    @Test
    void transientNotificationTemporarilyReplacesAndThenRestoresPersistentFrame() {
        Harness harness = new Harness();
        UUID playerId = UUID.randomUUID();
        harness.coordinator.publish(
                playerId,
                snapshot(playerId),
                List.of(frame("resource", "ACTION_BAR", 10, "resource", OptionalLong.empty())));

        harness.coordinator.publishNotification(
                playerId, frame("notice", "ACTION_BAR", 100, "ready", OptionalLong.of(1_500L)));
        assertEquals("ready", harness.output.actionBar);

        harness.now.set(1_500L);
        harness.coordinator.tick();

        assertEquals("resource", harness.output.actionBar);
    }

    @Test
    void notificationSurvivesPersistentSnapshotReplacement() {
        Harness harness = new Harness();
        UUID playerId = UUID.randomUUID();
        harness.coordinator.publishNotification(
                playerId, frame("notice", "ACTION_BAR", 100, "notice", OptionalLong.of(2_000L)));

        harness.coordinator.publish(
                playerId,
                snapshot(playerId),
                List.of(frame("resource", "ACTION_BAR", 10, "new-resource", OptionalLong.empty())));

        assertEquals("notice", harness.output.actionBar);
    }

    @Test
    void multipleBossBarsAreOwnedAndStaleBarsAreRemoved() {
        Harness harness = new Harness();
        UUID playerId = UUID.randomUUID();
        harness.coordinator.publish(
                playerId,
                snapshot(playerId),
                List.of(
                        frame("effect:fire", "BOSS_BAR", 1, "Fire", OptionalLong.empty()),
                        frame("effect:poison", "BOSS_BAR", 1, "Poison", OptionalLong.empty())));

        assertEquals(2, harness.output.bossBars.size());

        harness.coordinator.publish(
                playerId,
                snapshot(playerId),
                List.of(frame("effect:fire", "BOSS_BAR", 1, "Fire II", OptionalLong.empty())));

        assertEquals(1, harness.output.bossBars.size());
        assertEquals("Fire II", harness.output.bossBars.get("effect:fire"));
        assertEquals(List.of("effect:poison"), harness.output.removedBossBars);
    }

    @Test
    void expiredWidgetFramesAreNotPublishedAndAreRemovedOnTick() {
        Harness harness = new Harness();
        UUID playerId = UUID.randomUUID();
        harness.coordinator.publish(
                playerId,
                snapshot(playerId),
                List.of(
                        frame("resource", "ACTION_BAR", 10, "resource", OptionalLong.empty()),
                        frame("temporary", "BOSS_BAR", 1, "temporary", OptionalLong.of(1_100L))));
        assertEquals(1, harness.output.bossBars.size());

        harness.now.set(1_100L);
        harness.coordinator.tick();

        assertTrue(harness.output.bossBars.isEmpty());
        assertEquals("resource", harness.output.actionBar);
    }

    @Test
    void failedOutputIsRetriedOnNextSnapshotPublication() {
        Harness harness = new Harness();
        UUID playerId = UUID.randomUUID();
        harness.output.failNextActionBar = true;
        List<WidgetFrame> frames = List.of(frame("resource", "ACTION_BAR", 10, "resource", OptionalLong.empty()));

        assertThrows(
                IllegalStateException.class, () -> harness.coordinator.publish(playerId, snapshot(playerId), frames));
        harness.coordinator.publish(playerId, snapshot(playerId), frames);

        assertEquals("resource", harness.output.actionBar);
        assertEquals(1, harness.output.actionBarWrites);
    }

    @Test
    void scoreboardFramesAreRejectedUntilCompatibilityPolicyExists() {
        Harness harness = new Harness();
        UUID playerId = UUID.randomUUID();

        assertThrows(
                UnsupportedOperationException.class,
                () -> harness.coordinator.publish(
                        playerId,
                        snapshot(playerId),
                        List.of(frame("sidebar", "SCOREBOARD", 1, "not-enabled", OptionalLong.empty()))));

        assertEquals(0, harness.output.actionBarWrites);
        assertTrue(harness.output.bossBars.isEmpty());
    }

    @Test
    void clearPlayerRemovesOwnedOutputAndCloseIsIdempotent() {
        Harness harness = new Harness();
        UUID playerId = UUID.randomUUID();
        harness.coordinator.publish(
                playerId,
                snapshot(playerId),
                List.of(
                        frame("resource", "ACTION_BAR", 10, "resource", OptionalLong.empty()),
                        frame("effect", "BOSS_BAR", 1, "effect", OptionalLong.empty())));

        harness.coordinator.clearPlayer(playerId);

        assertEquals(0, harness.coordinator.trackedPlayerCount());
        assertEquals(1, harness.output.clearPlayerCalls);
        assertTrue(harness.output.bossBars.isEmpty());
        assertEquals(null, harness.output.actionBar);

        harness.coordinator.close();
        harness.coordinator.close();
        assertEquals(1, harness.output.clearAllCalls);
        assertThrows(IllegalStateException.class, () -> harness.coordinator.tick());
    }

    @Test
    void failedTickForOnePlayerDoesNotPreventOtherPlayersFromBeingReconciled() {
        Harness harness = new Harness();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        harness.coordinator.publish(
                first, snapshot(first), List.of(frame("first", "ACTION_BAR", 10, "first", OptionalLong.empty())));
        harness.coordinator.publish(
                second, snapshot(second), List.of(frame("second", "ACTION_BAR", 10, "second", OptionalLong.empty())));
        harness.output.failNextClear = true;
        harness.coordinator.publish(first, snapshot(first), List.of());

        assertThrows(IllegalStateException.class, harness.coordinator::tick);
        assertEquals("second", harness.output.actionBar);
    }

    @Test
    void notificationRequiresAnExpiry() {
        Harness harness = new Harness();
        UUID playerId = UUID.randomUUID();

        assertThrows(
                IllegalArgumentException.class,
                () -> harness.coordinator.publishNotification(
                        playerId, frame("notice", "ACTION_BAR", 100, "notice", OptionalLong.empty())));
    }

    private static WidgetFrame frame(String id, String channel, int priority, String payload, OptionalLong expiry) {
        return new WidgetFrame(id, channel, priority, payload, expiry);
    }

    private static PlayerHudSnapshot snapshot(UUID playerId) {
        return new PlayerHudSnapshot(
                playerId, 1L, 1_000L, null, null, null, null, null, null, List.of(), List.of(), null, null);
    }

    private static final class Harness {
        private final AtomicLong now = new AtomicLong(1_000L);
        private final RecordingOutput output = new RecordingOutput();
        private final HudChannelCoordinator coordinator =
                new HudChannelCoordinator(output, HudFailureReporterNoop.INSTANCE, now::get);
    }

    private static final class RecordingOutput implements HudChannelOutput {
        private final java.util.Map<String, String> bossBars = new java.util.HashMap<>();
        private final List<String> removedBossBars = new ArrayList<>();
        private String actionBar;
        private int actionBarWrites;
        private int clearPlayerCalls;
        private int clearAllCalls;
        private boolean failNextActionBar;
        private boolean failNextClear;

        @Override
        public void sendActionBar(UUID playerId, String payload) {
            if (failNextActionBar) {
                failNextActionBar = false;
                throw new IllegalStateException("actionbar unavailable");
            }
            actionBar = payload;
            actionBarWrites++;
        }

        @Override
        public void clearActionBar(UUID playerId) {
            if (failNextClear) {
                failNextClear = false;
                throw new IllegalStateException("actionbar clear failed");
            }
            actionBar = null;
        }

        @Override
        public void upsertBossBar(UUID playerId, String widgetId, String payload) {
            bossBars.put(widgetId, payload);
        }

        @Override
        public void removeBossBar(UUID playerId, String widgetId) {
            bossBars.remove(widgetId);
            removedBossBars.add(widgetId);
        }

        @Override
        public void clearPlayer(UUID playerId) {
            clearPlayerCalls++;
            actionBar = null;
            bossBars.clear();
        }

        @Override
        public void clearAll() {
            clearAllCalls++;
            actionBar = null;
            bossBars.clear();
        }
    }

    private enum HudFailureReporterNoop implements HudFailureReporter {
        INSTANCE;

        @Override
        public void report(UUID playerId, String stage, RuntimeException failure) {}
    }
}

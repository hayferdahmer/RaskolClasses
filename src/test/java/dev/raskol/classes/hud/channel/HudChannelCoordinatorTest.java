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

        assertEquals("A", harness.output.actionBars.get(playerId));
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
        assertEquals("ready", harness.output.actionBars.get(playerId));

        harness.now.set(1_500L);
        harness.coordinator.tick();

        assertEquals("resource", harness.output.actionBars.get(playerId));
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

        assertEquals("notice", harness.output.actionBars.get(playerId));
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

        assertEquals(2, harness.output.bossBars.get(playerId).size());

        harness.coordinator.publish(
                playerId,
                snapshot(playerId),
                List.of(frame("effect:fire", "BOSS_BAR", 1, "Fire II", OptionalLong.empty())));

        assertEquals(1, harness.output.bossBars.get(playerId).size());
        assertEquals("Fire II", harness.output.bossBars.get(playerId).get("effect:fire"));
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
        assertEquals(1, harness.output.bossBars.get(playerId).size());

        harness.now.set(1_100L);
        harness.coordinator.tick();

        assertTrue(harness.output.bossBars.isEmpty());
        assertEquals("resource", harness.output.actionBars.get(playerId));
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

        assertEquals("resource", harness.output.actionBars.get(playerId));
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
        assertTrue(!harness.output.actionBars.containsKey(playerId));

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
        harness.coordinator.publishNotification(
                first, frame("first-notice", "ACTION_BAR", 100, "first", OptionalLong.of(1_100L)));
        harness.now.set(1_100L);
        harness.output.failClearPlayers.add(first);

        assertThrows(IllegalStateException.class, harness.coordinator::tick);
        assertEquals("second", harness.output.actionBars.get(second));
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
        HudValue<String> unavailableText = HudValue.unavailable();
        HudValue<Double> unavailableNumber = HudValue.unavailable();
        return new PlayerHudSnapshot(
                playerId,
                1L,
                1_000L,
                new PlayerHudSnapshot.CharacterView(
                        unavailableText,
                        unavailableText,
                        unavailableText,
                        unavailableText,
                        HudValue.available(1),
                        HudValue.available(0L),
                        HudValue.available(100L)),
                new PlayerHudSnapshot.HealthView(unavailableNumber, unavailableNumber),
                new PlayerHudSnapshot.ResourceView(
                        unavailableText, unavailableText, unavailableText, unavailableNumber, unavailableNumber),
                new PlayerHudSnapshot.AttributeView(unavailableNumber, unavailableNumber, unavailableNumber),
                TargetReadResult.none(),
                List.of(),
                List.of(),
                new PlayerHudSnapshot.CombatView(HudValue.available(false), HudValue.notApplicable()),
                new PlayerHudSnapshot.HudProfileView(HudValue.available("default"), java.util.Set.of()));
    }

    private static final class Harness {
        private final AtomicLong now = new AtomicLong(1_000L);
        private final RecordingOutput output = new RecordingOutput();
        private final HudChannelCoordinator coordinator =
                new HudChannelCoordinator(output, HudFailureReporterNoop.INSTANCE, now::get);
    }

    private static final class RecordingOutput implements HudChannelOutput {
        private final java.util.Map<UUID, java.util.Map<String, String>> bossBars = new java.util.HashMap<>();
        private final java.util.Map<UUID, String> actionBars = new java.util.HashMap<>();
        private final List<String> removedBossBars = new ArrayList<>();
        private int actionBarWrites;
        private int clearPlayerCalls;
        private int clearAllCalls;
        private boolean failNextActionBar;
        private final java.util.Set<UUID> failClearPlayers = new java.util.HashSet<>();

        @Override
        public void sendActionBar(UUID playerId, String payload) {
            if (failNextActionBar) {
                failNextActionBar = false;
                throw new IllegalStateException("actionbar unavailable");
            }
            actionBars.put(playerId, payload);
            actionBarWrites++;
        }

        @Override
        public void clearActionBar(UUID playerId) {
            if (failClearPlayers.remove(playerId)) {
                throw new IllegalStateException("actionbar clear failed");
            }
            actionBars.remove(playerId);
        }

        @Override
        public void upsertBossBar(UUID playerId, String widgetId, String payload) {
            bossBars.computeIfAbsent(playerId, ignored -> new java.util.HashMap<>())
                    .put(widgetId, payload);
        }

        @Override
        public void removeBossBar(UUID playerId, String widgetId) {
            java.util.Map<String, String> playerBars = bossBars.get(playerId);
            if (playerBars != null) {
                playerBars.remove(widgetId);
                if (playerBars.isEmpty()) {
                    bossBars.remove(playerId);
                }
            }
            removedBossBars.add(widgetId);
        }

        @Override
        public void clearPlayer(UUID playerId) {
            clearPlayerCalls++;
            actionBars.remove(playerId);
            bossBars.remove(playerId);
        }

        @Override
        public void clearAll() {
            clearAllCalls++;
            actionBars.clear();
            bossBars.clear();
        }
    }

    private enum HudFailureReporterNoop implements HudFailureReporter {
        INSTANCE;

        @Override
        public void report(UUID playerId, String stage, RuntimeException failure) {}
    }
}

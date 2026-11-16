// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.hud.snapshot.DataStatus;
import dev.raskol.classes.hud.snapshot.HudFailureReporter;
import dev.raskol.classes.hud.snapshot.HudFramePublisher;
import dev.raskol.classes.hud.snapshot.HudSnapshotProvider;
import dev.raskol.classes.hud.snapshot.HudValue;
import dev.raskol.classes.hud.snapshot.HudWidget;
import dev.raskol.classes.hud.snapshot.HudWidgetRegistry;
import dev.raskol.classes.hud.snapshot.PlayerHudSnapshot;
import dev.raskol.classes.hud.snapshot.WidgetFrame;
import dev.raskol.classes.targeting.domain.TargetReadResult;

class HudCoordinatorTest {

    @Test
    void coalescesRepeatedDirtySignalsAndPublishesOneImmutableSnapshot() {
        UUID playerId = UUID.randomUUID();
        RecordingPublisher publisher = new RecordingPublisher();
        HudWidgetRegistry widgets = new HudWidgetRegistry();
        widgets.register(widget("health", snapshot -> Optional.of(frame("health", "hp"))));
        HudCoordinator coordinator = coordinator((id, revision) -> snapshot(id, revision), widgets, publisher, 4);

        coordinator.markDirty(playerId);
        coordinator.markDirty(playerId);
        coordinator.markDirty(playerId);
        HudCoordinator.RefreshReport report = coordinator.tick(List.of(playerId));

        assertEquals(1, report.attempted());
        assertEquals(1, report.published());
        assertEquals(1, publisher.snapshots.size());
        assertEquals(1L, coordinator.latestSnapshot(playerId).revision());
        assertEquals(
                List.of("health"),
                publisher.frames.getFirst().stream().map(WidgetFrame::widgetId).toList());
    }

    @Test
    void refreshBudgetBoundsDirtyAndFallbackWork() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        AtomicInteger captures = new AtomicInteger();
        HudCoordinator coordinator = coordinator(
                (id, revision) -> {
                    captures.incrementAndGet();
                    return snapshot(id, revision);
                },
                new HudWidgetRegistry(),
                new RecordingPublisher(),
                2);

        coordinator.markDirty(first);
        coordinator.markDirty(second);
        coordinator.markDirty(third);
        HudCoordinator.RefreshReport firstTick = coordinator.tick(List.of(first, second, third));
        HudCoordinator.RefreshReport secondTick = coordinator.tick(List.of(first, second, third));

        assertEquals(2, firstTick.attempted());
        assertEquals(2, secondTick.attempted());
        assertEquals(4, captures.get());
        assertEquals(1, coordinator.dirtyPlayerCount());
    }

    @Test
    void snapshotFailureIsReportedAndRetriedOnNextTick() {
        UUID playerId = UUID.randomUUID();
        AtomicBoolean fail = new AtomicBoolean(true);
        List<String> stages = new ArrayList<>();
        HudCoordinator coordinator = new HudCoordinator(
                (id, revision) -> {
                    if (fail.getAndSet(false)) {
                        throw new IllegalStateException("source unavailable");
                    }
                    return snapshot(id, revision);
                },
                new HudWidgetRegistry(),
                new RecordingPublisher(),
                (id, stage, failure) -> stages.add(stage),
                1);
        coordinator.markDirty(playerId);

        HudCoordinator.RefreshReport failed = coordinator.tick(List.of(playerId));
        HudCoordinator.RefreshReport retried = coordinator.tick(List.of(playerId));

        assertEquals(1, failed.snapshotFailures());
        assertEquals(1, retried.published());
        assertEquals(List.of("snapshot"), stages);
        assertEquals(1L, coordinator.latestSnapshot(playerId).revision());
    }

    @Test
    void oneWidgetFailureDoesNotSuppressOtherWidgets() {
        UUID playerId = UUID.randomUUID();
        HudWidgetRegistry widgets = new HudWidgetRegistry();
        widgets.register(widget("a-failing", snapshot -> {
            throw new IllegalStateException("widget failure");
        }));
        widgets.register(widget("b-working", snapshot -> Optional.of(frame("b-working", "ok"))));
        RecordingPublisher publisher = new RecordingPublisher();
        List<String> failures = new ArrayList<>();
        HudCoordinator coordinator = new HudCoordinator(
                (id, revision) -> snapshot(id, revision),
                widgets,
                publisher,
                (id, stage, failure) -> failures.add(stage),
                1);
        coordinator.markDirty(playerId);

        HudCoordinator.RefreshReport report = coordinator.tick(List.of(playerId));

        assertEquals(1, report.published());
        assertEquals(1, report.widgetFailures());
        assertEquals(List.of("widget:a-failing"), failures);
        assertEquals(
                List.of("b-working"),
                publisher.frames.getFirst().stream().map(WidgetFrame::widgetId).toList());
    }

    @Test
    void publisherFailureRetriesWithoutAdvancingPublishedRevision() {
        UUID playerId = UUID.randomUUID();
        RecordingPublisher publisher = new RecordingPublisher();
        publisher.failOnce = true;
        HudCoordinator coordinator =
                coordinator((id, revision) -> snapshot(id, revision), new HudWidgetRegistry(), publisher, 1);
        coordinator.markDirty(playerId);

        assertEquals(1, coordinator.tick(List.of(playerId)).publishFailures());
        assertNull(coordinator.latestSnapshot(playerId));
        assertEquals(1, coordinator.tick(List.of(playerId)).published());
        assertEquals(1L, coordinator.latestSnapshot(playerId).revision());
    }

    @Test
    void quitAndCloseClearPublishedPlayerState() {
        UUID playerId = UUID.randomUUID();
        RecordingPublisher publisher = new RecordingPublisher();
        HudCoordinator coordinator =
                coordinator((id, revision) -> snapshot(id, revision), new HudWidgetRegistry(), publisher, 2);
        coordinator.markDirty(playerId);
        coordinator.tick(List.of(playerId));

        coordinator.onPlayerQuit(playerId);
        assertNull(coordinator.latestSnapshot(playerId));
        assertEquals(List.of(playerId), publisher.cleared);

        coordinator.markDirty(playerId);
        coordinator.tick(List.of(playerId));
        coordinator.close();
        assertEquals(List.of(playerId, playerId), publisher.cleared);
        assertThrows(IllegalStateException.class, () -> coordinator.markDirty(playerId));
    }

    @Test
    void unavailableValuesAreNotRepresentedAsZero() {
        HudValue<Double> unavailable = HudValue.unavailable();
        assertEquals(DataStatus.UNAVAILABLE, unavailable.status());
        assertTrue(unavailable.optionalValue().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new HudValue<>(DataStatus.UNAVAILABLE, 0.0d));
    }

    private static HudCoordinator coordinator(
            HudSnapshotProvider provider, HudWidgetRegistry widgets, RecordingPublisher publisher, int budget) {
        return new HudCoordinator(provider, widgets, publisher, HudFailureReporterNoop.INSTANCE, budget);
    }

    private static PlayerHudSnapshot snapshot(UUID playerId, long revision) {
        HudValue<String> unavailableText = HudValue.unavailable();
        HudValue<Double> unavailableNumber = HudValue.unavailable();
        return new PlayerHudSnapshot(
                playerId,
                revision,
                1_000L + revision,
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
                new PlayerHudSnapshot.HudProfileView(HudValue.available("default"), Set.of()));
    }

    private static HudWidget widget(
            String id, java.util.function.Function<PlayerHudSnapshot, Optional<WidgetFrame>> evaluator) {
        return new HudWidget() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Optional<WidgetFrame> evaluate(PlayerHudSnapshot snapshot) {
                return evaluator.apply(snapshot);
            }
        };
    }

    private static WidgetFrame frame(String widgetId, String payload) {
        return new WidgetFrame(widgetId, "ACTION_BAR", 10, payload, java.util.OptionalLong.empty());
    }

    private static final class RecordingPublisher implements HudFramePublisher {
        private final List<PlayerHudSnapshot> snapshots = new ArrayList<>();
        private final List<List<WidgetFrame>> frames = new ArrayList<>();
        private final List<UUID> cleared = new ArrayList<>();
        private boolean failOnce;

        @Override
        public void publish(UUID playerId, PlayerHudSnapshot snapshot, List<WidgetFrame> widgetFrames) {
            if (failOnce) {
                failOnce = false;
                throw new IllegalStateException("publisher unavailable");
            }
            snapshots.add(snapshot);
            frames.add(widgetFrames);
        }

        @Override
        public void clearPlayer(UUID playerId) {
            cleared.add(playerId);
        }
    }

    private enum HudFailureReporterNoop implements HudFailureReporter {
        INSTANCE;

        @Override
        public void report(UUID playerId, String stage, RuntimeException failure) {}
    }
}

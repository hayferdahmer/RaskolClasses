// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import dev.raskol.classes.hud.snapshot.HudFailureReporter;
import dev.raskol.classes.hud.snapshot.HudFramePublisher;
import dev.raskol.classes.hud.snapshot.HudSnapshotProvider;
import dev.raskol.classes.hud.snapshot.HudWidget;
import dev.raskol.classes.hud.snapshot.HudWidgetRegistry;
import dev.raskol.classes.hud.snapshot.PlayerHudSnapshot;
import dev.raskol.classes.hud.snapshot.WidgetFrame;

/** Координирует dirty coalescing, ограниченный fallback refresh и вычисление виджетов. */
public final class HudCoordinator implements AutoCloseable {
    private final HudSnapshotProvider snapshotProvider;
    private final HudWidgetRegistry widgetRegistry;
    private final HudFramePublisher publisher;
    private final HudFailureReporter failureReporter;
    private final int maxRefreshesPerTick;
    private final LinkedHashSet<UUID> dirtyPlayers = new LinkedHashSet<>();
    private final Map<UUID, Long> revisions = new HashMap<>();
    private final Map<UUID, PlayerHudSnapshot> latestSnapshots = new HashMap<>();
    private int fallbackCursor;
    private boolean closed;

    public HudCoordinator(
            HudSnapshotProvider snapshotProvider,
            HudWidgetRegistry widgetRegistry,
            HudFramePublisher publisher,
            HudFailureReporter failureReporter,
            int maxRefreshesPerTick) {
        this.snapshotProvider = Objects.requireNonNull(snapshotProvider, "snapshotProvider");
        this.widgetRegistry = Objects.requireNonNull(widgetRegistry, "widgetRegistry");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.failureReporter = Objects.requireNonNull(failureReporter, "failureReporter");
        if (maxRefreshesPerTick <= 0) {
            throw new IllegalArgumentException("maxRefreshesPerTick должен быть положительным");
        }
        this.maxRefreshesPerTick = maxRefreshesPerTick;
    }

    /** Несколько изменений одного игрока до tick объединяются в одну запись. */
    public synchronized void markDirty(UUID playerId) {
        ensureOpen();
        dirtyPlayers.add(Objects.requireNonNull(playerId, "playerId"));
    }

    /**
     * Обрабатывает сначала dirty players, затем ограниченный fallback по online players.
     * Все обращения к provider и publisher выполняются вызывающим потоком.
     */
    public synchronized RefreshReport tick(Collection<UUID> onlinePlayers) {
        ensureOpen();
        Objects.requireNonNull(onlinePlayers, "onlinePlayers");
        List<UUID> online = onlinePlayers.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Set<UUID> onlineSet = new HashSet<>(online);
        dirtyPlayers.removeIf(playerId -> !onlineSet.contains(playerId));

        int attempted = 0;
        int published = 0;
        int snapshotFailures = 0;
        int widgetFailures = 0;
        int publishFailures = 0;
        Set<UUID> attemptedPlayers = new HashSet<>();
        List<UUID> retry = new ArrayList<>();

        Iterator<UUID> dirtyIterator = dirtyPlayers.iterator();
        while (attempted < maxRefreshesPerTick && dirtyIterator.hasNext()) {
            UUID playerId = dirtyIterator.next();
            dirtyIterator.remove();
            attemptedPlayers.add(playerId);
            attempted++;
            RefreshOutcome outcome = refresh(playerId);
            if (outcome.published()) {
                published++;
            }
            if (outcome.snapshotFailed()) {
                snapshotFailures++;
                retry.add(playerId);
            }
            widgetFailures += outcome.widgetFailures();
            if (outcome.publishFailed()) {
                publishFailures++;
                retry.add(playerId);
            }
        }

        if (attempted < maxRefreshesPerTick && !online.isEmpty()) {
            int scanned = 0;
            while (attempted < maxRefreshesPerTick && scanned < online.size()) {
                fallbackCursor = Math.floorMod(fallbackCursor, online.size());
                UUID playerId = online.get(fallbackCursor);
                fallbackCursor = (fallbackCursor + 1) % online.size();
                scanned++;
                if (attemptedPlayers.contains(playerId) || dirtyPlayers.contains(playerId)) {
                    continue;
                }
                attemptedPlayers.add(playerId);
                attempted++;
                RefreshOutcome outcome = refresh(playerId);
                if (outcome.published()) {
                    published++;
                }
                if (outcome.snapshotFailed()) {
                    snapshotFailures++;
                    retry.add(playerId);
                }
                widgetFailures += outcome.widgetFailures();
                if (outcome.publishFailed()) {
                    publishFailures++;
                    retry.add(playerId);
                }
            }
        }

        dirtyPlayers.addAll(retry);
        return new RefreshReport(attempted, published, snapshotFailures, widgetFailures, publishFailures, dirtyPlayers.size());
    }

    public synchronized PlayerHudSnapshot latestSnapshot(UUID playerId) {
        return latestSnapshots.get(Objects.requireNonNull(playerId, "playerId"));
    }

    public synchronized int dirtyPlayerCount() {
        return dirtyPlayers.size();
    }

    /** Удаляет весь per-player runtime state; вызывается из session lifecycle. */
    public synchronized void onPlayerQuit(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        dirtyPlayers.remove(playerId);
        revisions.remove(playerId);
        latestSnapshots.remove(playerId);
        try {
            publisher.clearPlayer(playerId);
        } catch (RuntimeException failure) {
            report(playerId, "publisher-clear-player", failure);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        dirtyPlayers.clear();
        RuntimeException aggregate = null;
        for (UUID playerId : List.copyOf(latestSnapshots.keySet())) {
            try {
                publisher.clearPlayer(playerId);
            } catch (RuntimeException failure) {
                if (aggregate == null) {
                    aggregate = new IllegalStateException("Не удалось полностью очистить HUD snapshot publisher");
                }
                aggregate.addSuppressed(failure);
                report(playerId, "publisher-close", failure);
            }
        }
        latestSnapshots.clear();
        revisions.clear();
        if (aggregate != null) {
            throw aggregate;
        }
    }

    private RefreshOutcome refresh(UUID playerId) {
        long nextRevision = revisions.getOrDefault(playerId, 0L) + 1L;
        final PlayerHudSnapshot snapshot;
        try {
            snapshot = Objects.requireNonNull(snapshotProvider.capture(playerId, nextRevision), "snapshot");
            if (!snapshot.playerId().equals(playerId) || snapshot.revision() != nextRevision) {
                throw new IllegalStateException("Snapshot provider вернул неверные playerId/revision");
            }
        } catch (RuntimeException failure) {
            report(playerId, "snapshot", failure);
            return RefreshOutcome.snapshotFailure();
        }

        List<WidgetFrame> frames = new ArrayList<>();
        int widgetFailures = 0;
        for (HudWidget widget : widgetRegistry.widgets()) {
            try {
                widget.evaluate(snapshot).ifPresent(frame -> {
                    if (!widget.id().equals(frame.widgetId())) {
                        throw new IllegalStateException("WidgetFrame ID не совпадает с ID виджета: " + widget.id());
                    }
                    frames.add(frame);
                });
            } catch (RuntimeException failure) {
                widgetFailures++;
                report(playerId, "widget:" + widget.id(), failure);
            }
        }

        try {
            publisher.publish(playerId, snapshot, List.copyOf(frames));
        } catch (RuntimeException failure) {
            report(playerId, "publisher", failure);
            return RefreshOutcome.publishFailure(widgetFailures);
        }

        revisions.put(playerId, nextRevision);
        latestSnapshots.put(playerId, snapshot);
        return RefreshOutcome.success(widgetFailures);
    }

    private void report(UUID playerId, String stage, RuntimeException failure) {
        try {
            failureReporter.report(playerId, stage, failure);
        } catch (RuntimeException reporterFailure) {
            failure.addSuppressed(reporterFailure);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("HudCoordinator уже закрыт");
        }
    }

    private record RefreshOutcome(boolean published, boolean snapshotFailed, boolean publishFailed, int widgetFailures) {
        private static RefreshOutcome success(int widgetFailures) {
            return new RefreshOutcome(true, false, false, widgetFailures);
        }

        private static RefreshOutcome snapshotFailure() {
            return new RefreshOutcome(false, true, false, 0);
        }

        private static RefreshOutcome publishFailure(int widgetFailures) {
            return new RefreshOutcome(false, false, true, widgetFailures);
        }
    }

    public record RefreshReport(
            int attempted,
            int published,
            int snapshotFailures,
            int widgetFailures,
            int publishFailures,
            int remainingDirtyPlayers) {}
}

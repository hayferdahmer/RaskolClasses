// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.channel;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.LongSupplier;

import dev.raskol.classes.hud.snapshot.HudFailureReporter;
import dev.raskol.classes.hud.snapshot.HudFramePublisher;
import dev.raskol.classes.hud.snapshot.PlayerHudSnapshot;
import dev.raskol.classes.hud.snapshot.WidgetFrame;

/**
 * Единый владелец ActionBar и HUD-owned BossBar output.
 *
 * <p>Снимок полностью заменяет набор persistent widget frames, а уведомления
 * живут отдельно и вытесняют ActionBar только на время своего TTL.</p>
 */
public final class HudChannelCoordinator implements HudFramePublisher, AutoCloseable {
    private static final String ACTION_BAR = HudChannel.ACTION_BAR.name();
    private static final String BOSS_BAR = HudChannel.BOSS_BAR.name();
    private static final String SCOREBOARD = HudChannel.SCOREBOARD.name();

    private static final Comparator<FrameEntry> ACTION_BAR_ORDER = Comparator.comparingInt(
                    (FrameEntry entry) -> entry.frame().priority())
            .reversed()
            .thenComparing(Comparator.comparingLong(FrameEntry::sequence).reversed())
            .thenComparing(entry -> entry.frame().widgetId());

    private final HudChannelOutput output;
    private final HudFailureReporter failureReporter;
    private final LongSupplier clock;
    private final Map<UUID, PlayerChannels> players = new LinkedHashMap<>();

    private long nextSequence;
    private boolean closing;
    private boolean closed;

    public HudChannelCoordinator(HudChannelOutput output, HudFailureReporter failureReporter, LongSupplier clock) {
        this.output = Objects.requireNonNull(output, "output");
        this.failureReporter = Objects.requireNonNull(failureReporter, "failureReporter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Публикует полный набор кадров snapshot, не затрагивая очередь уведомлений. */
    @Override
    public synchronized void publish(UUID playerId, PlayerHudSnapshot snapshot, List<WidgetFrame> frames) {
        ensureOpen();
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(frames, "frames");
        UUID id = Objects.requireNonNull(playerId, "playerId");
        if (!snapshot.playerId().equals(id)) {
            throw new IllegalArgumentException("PlayerHudSnapshot принадлежит другому игроку");
        }

        long now = clock.getAsLong();
        Map<String, FrameEntry> replacement = new LinkedHashMap<>();
        long publicationSequence = nextSequence++;
        for (WidgetFrame frame : List.copyOf(frames)) {
            validateSupportedChannel(frame);
            if (isExpired(frame, now)) {
                continue;
            }
            if (replacement.putIfAbsent(frame.widgetId(), new FrameEntry(frame, publicationSequence)) != null) {
                throw new IllegalArgumentException("Повторный HUD widgetId в одном snapshot: " + frame.widgetId());
            }
        }

        PlayerChannels state = players.computeIfAbsent(id, ignored -> new PlayerChannels());
        for (String widgetId : replacement.keySet()) {
            if (state.persistentFrames.containsKey(widgetId) || state.notifications.containsKey(widgetId)) {
                throw new IllegalArgumentException("HUD widgetId уже принадлежит другому producer: " + widgetId);
            }
        }
        state.snapshotFrames.clear();
        state.snapshotFrames.putAll(replacement);
        reconcileSafely(id, state, now, "publish");
        removeIfEmpty(id, state);
    }

    /**
     * Публикует или обновляет один постоянный кадр legacy producer.
     * Кадр живёт отдельно от snapshot frames и не стирается следующим snapshot.
     */
    public synchronized void upsertPersistentFrame(UUID playerId, WidgetFrame frame) {
        ensureOpen();
        UUID id = Objects.requireNonNull(playerId, "playerId");
        validateSupportedChannel(frame);
        long now = clock.getAsLong();
        PlayerChannels state = players.computeIfAbsent(id, ignored -> new PlayerChannels());
        if (state.snapshotFrames.containsKey(frame.widgetId()) || state.notifications.containsKey(frame.widgetId())) {
            throw new IllegalArgumentException("HUD widgetId уже принадлежит другому producer: " + frame.widgetId());
        }
        if (isExpired(frame, now)) {
            state.persistentFrames.remove(frame.widgetId());
        } else {
            state.persistentFrames.put(frame.widgetId(), new FrameEntry(frame, nextSequence++));
        }
        reconcileSafely(id, state, now, "persistent-frame");
        removeIfEmpty(id, state);
    }

    /** Удаляет один постоянный кадр, не затрагивая другие producers и уведомления. */
    public synchronized void removePersistentFrame(UUID playerId, String widgetId) {
        ensureOpen();
        UUID id = Objects.requireNonNull(playerId, "playerId");
        PlayerChannels state = players.get(id);
        if (state == null) {
            return;
        }
        state.persistentFrames.remove(Objects.requireNonNull(widgetId, "widgetId"));
        reconcileSafely(id, state, clock.getAsLong(), "persistent-frame-remove");
        removeIfEmpty(id, state);
    }

    /**
     * Добавляет отдельное временное уведомление. Уведомления не входят в snapshot
     * и не исчезают при очередном обновлении постоянных виджетов.
     */
    public synchronized void publishNotification(UUID playerId, WidgetFrame notification) {
        ensureOpen();
        UUID id = Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(notification, "notification");
        if (!ACTION_BAR.equals(notification.channel())) {
            throw new IllegalArgumentException("HUD-уведомление должно использовать ACTION_BAR");
        }
        if (notification.expiresAtMillis().isEmpty()) {
            throw new IllegalArgumentException("HUD-уведомление должно иметь конечный TTL");
        }

        long now = clock.getAsLong();
        PlayerChannels state = players.computeIfAbsent(id, ignored -> new PlayerChannels());
        if (state.snapshotFrames.containsKey(notification.widgetId())
                || state.persistentFrames.containsKey(notification.widgetId())) {
            throw new IllegalArgumentException(
                    "HUD notification ID конфликтует с постоянным widgetId: " + notification.widgetId());
        }
        if (isExpired(notification, now)) {
            state.notifications.remove(notification.widgetId());
            reconcileSafely(id, state, now, "notification-expired");
            removeIfEmpty(id, state);
            return;
        }
        state.notifications.put(notification.widgetId(), new FrameEntry(notification, nextSequence++));
        reconcileSafely(id, state, now, "notification");
    }

    /** Снимает уведомление по стабильному source/widget ID. */
    public synchronized void cancelNotification(UUID playerId, String notificationId) {
        ensureOpen();
        UUID id = Objects.requireNonNull(playerId, "playerId");
        PlayerChannels state = players.get(id);
        if (state == null) {
            return;
        }
        state.notifications.remove(Objects.requireNonNull(notificationId, "notificationId"));
        reconcileSafely(id, state, clock.getAsLong(), "notification-cancel");
        removeIfEmpty(id, state);
    }

    /** Удаляет истёкшие TTL и восстанавливает следующий доступный ActionBar frame. */
    public synchronized void tick() {
        ensureOpen();
        long now = clock.getAsLong();
        RuntimeException aggregate = null;
        for (Map.Entry<UUID, PlayerChannels> entry : List.copyOf(players.entrySet())) {
            UUID playerId = entry.getKey();
            PlayerChannels state = entry.getValue();
            state.snapshotFrames.values().removeIf(frame -> isExpired(frame.frame(), now));
            state.persistentFrames.values().removeIf(frame -> isExpired(frame.frame(), now));
            state.notifications.values().removeIf(frame -> isExpired(frame.frame(), now));
            try {
                reconcile(playerId, state, now);
                removeIfEmpty(playerId, state);
            } catch (RuntimeException failure) {
                report(playerId, "tick", failure);
                aggregate = merge(aggregate, failure);
            }
        }
        if (aggregate != null) {
            throw aggregate;
        }
    }

    /** Очищает все каналы и состояние одного игрока. */
    @Override
    public synchronized void clearPlayer(UUID playerId) {
        UUID id = Objects.requireNonNull(playerId, "playerId");
        try {
            output.clearPlayer(id);
            players.remove(id);
        } catch (RuntimeException failure) {
            report(id, "clear-player", failure);
            throw failure;
        }
    }

    /** Очищает все output handles; при ошибке состояние сохраняется для retry. */
    public synchronized void clearAll() {
        RuntimeException failure = null;
        try {
            output.clearAll();
            players.clear();
        } catch (RuntimeException exception) {
            failure = exception;
            report(null, "clear-all", exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    public synchronized int trackedPlayerCount() {
        return players.size();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closing = true;
        clearAll();
        closed = true;
        closing = false;
    }

    private void reconcileSafely(UUID playerId, PlayerChannels state, long now, String stage) {
        try {
            reconcile(playerId, state, now);
        } catch (RuntimeException failure) {
            report(playerId, stage, failure);
            throw failure;
        }
    }

    private void reconcile(UUID playerId, PlayerChannels state, long now) {
        FrameEntry winner = selectActionBar(state, now);
        WidgetFrame desiredActionBar = winner == null ? null : winner.frame();
        if (!sameActionBar(state.renderedActionBar, desiredActionBar)) {
            if (desiredActionBar == null) {
                if (state.renderedActionBar != null) {
                    output.clearActionBar(playerId);
                }
            } else {
                output.sendActionBar(playerId, desiredActionBar.payload());
            }
            state.renderedActionBar = desiredActionBar;
        }

        Map<String, WidgetFrame> desiredBossBars = new HashMap<>();
        for (FrameEntry entry : allPersistentFrames(state)) {
            WidgetFrame frame = entry.frame();
            if (BOSS_BAR.equals(frame.channel()) && !isExpired(frame, now)) {
                desiredBossBars.put(frame.widgetId(), frame);
            }
        }

        for (String widgetId : List.copyOf(state.renderedBossBars.keySet())) {
            if (!desiredBossBars.containsKey(widgetId)) {
                output.removeBossBar(playerId, widgetId);
                state.renderedBossBars.remove(widgetId);
            }
        }
        for (Map.Entry<String, WidgetFrame> entry : desiredBossBars.entrySet()) {
            WidgetFrame rendered = state.renderedBossBars.get(entry.getKey());
            if (!Objects.equals(rendered, entry.getValue())) {
                output.upsertBossBar(playerId, entry.getKey(), entry.getValue());
                state.renderedBossBars.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private static List<FrameEntry> allPersistentFrames(PlayerChannels state) {
        return java.util.stream.Stream.concat(
                        state.snapshotFrames.values().stream(), state.persistentFrames.values().stream())
                .toList();
    }

    private static FrameEntry selectActionBar(PlayerChannels state, long now) {
        return java.util.stream.Stream.concat(
                        allPersistentFrames(state).stream(), state.notifications.values().stream())
                .filter(entry -> ACTION_BAR.equals(entry.frame().channel()))
                .filter(entry -> !isExpired(entry.frame(), now))
                .min(ACTION_BAR_ORDER)
                .orElse(null);
    }

    private static boolean sameActionBar(WidgetFrame current, WidgetFrame desired) {
        if (current == desired) {
            return true;
        }
        return current != null
                && desired != null
                && current.widgetId().equals(desired.widgetId())
                && current.payload().equals(desired.payload());
    }

    private static void validateSupportedChannel(WidgetFrame frame) {
        Objects.requireNonNull(frame, "frame");
        if (SCOREBOARD.equals(frame.channel())) {
            throw new UnsupportedOperationException(
                    "Scoreboard HUD отключён до утверждения политики совместимости с другими плагинами");
        }
        if (!ACTION_BAR.equals(frame.channel()) && !BOSS_BAR.equals(frame.channel())) {
            throw new IllegalArgumentException("Неизвестный HUD channel: " + frame.channel());
        }
    }

    private static boolean isExpired(WidgetFrame frame, long now) {
        OptionalLong expiry = frame.expiresAtMillis();
        return expiry.isPresent() && expiry.getAsLong() <= now;
    }

    private void removeIfEmpty(UUID playerId, PlayerChannels state) {
        if (state.snapshotFrames.isEmpty()
                && state.persistentFrames.isEmpty()
                && state.notifications.isEmpty()
                && state.renderedActionBar == null
                && state.renderedBossBars.isEmpty()) {
            players.remove(playerId, state);
        }
    }

    private void report(UUID playerId, String stage, RuntimeException failure) {
        try {
            failureReporter.report(playerId, "channel-" + stage, failure);
        } catch (RuntimeException reporterFailure) {
            failure.addSuppressed(reporterFailure);
        }
    }

    private void ensureOpen() {
        if (closed || closing) {
            throw new IllegalStateException("HudChannelCoordinator уже закрывается или закрыт");
        }
    }

    private static RuntimeException merge(RuntimeException current, RuntimeException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private static final class PlayerChannels {
        private final Map<String, FrameEntry> snapshotFrames = new LinkedHashMap<>();
        private final Map<String, FrameEntry> persistentFrames = new LinkedHashMap<>();
        private final Map<String, FrameEntry> notifications = new LinkedHashMap<>();
        private final Map<String, WidgetFrame> renderedBossBars = new HashMap<>();
        private WidgetFrame renderedActionBar;
    }

    private record FrameEntry(WidgetFrame frame, long sequence) {}
}

// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.build;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import dev.raskol.classes.storage.AsyncStorageWriter;
import dev.raskol.classes.storage.SafeStorage;

/**
 * Асинхронное хранилище сроков raskolspec+ и подарочных уведомлений.
 * Снимок YAML создаётся до передачи в фоновый writer; Bukkit API в worker не вызывается.
 */
public final class PlusStorage {
    private static final Logger LOG = Logger.getLogger("RaskolClasses");
    private final File file;
    private final AsyncStorageWriter writer;
    private YamlConfiguration store;
    private final Map<UUID, Long> expires = new HashMap<>();
    private final Map<UUID, GiftNote> pendingGiftNotes = new HashMap<>();
    private boolean writeInFlight;
    private CompletableFuture<Void> writeIdle = CompletableFuture.completedFuture(null);

    public record GiftNote(String id, String message) {}

    public PlusStorage(File folder, AsyncStorageWriter writer) {
        this.file = new File(folder, "plus-subscription.yml");
        this.writer = writer;
        this.store = SafeStorage.loadWithFallback(file, LOG);
        loadFromStore();
    }

    private synchronized void loadFromStore() {
        expires.clear();
        pendingGiftNotes.clear();
        var players = store.getConfigurationSection("players");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                var sec = players.getConfigurationSection(key);
                if (sec == null) continue;
                long exp = sec.getLong("expires", 0L);
                if (exp > 0L) expires.put(uuid, exp);
                String note = sec.getString("gift-note", null);
                if (note != null && !note.isBlank()) {
                    String noteId = sec.getString("gift-note-id", legacyGiftNoteId(uuid, note));
                    pendingGiftNotes.put(uuid, new GiftNote(noteId, note));
                }
            } catch (IllegalArgumentException ignored) {
                // Некорректные UUID пропускаются, остальные записи сохраняются.
            }
        }
    }

    public synchronized long expiresAt(UUID uuid) {
        return uuid == null ? 0L : expires.getOrDefault(uuid, 0L);
    }

    public synchronized boolean writeInFlight() {
        return writeInFlight;
    }

    public synchronized CompletableFuture<Boolean> setExpiresAsync(UUID uuid, long epochMillis) {
        if (uuid == null || epochMillis < 0L || writeInFlight) return CompletableFuture.completedFuture(false);
        YamlConfiguration candidate = copyStore();
        if (candidate == null) return CompletableFuture.completedFuture(false);
        candidate.set("players." + uuid + ".expires", epochMillis == 0L ? null : epochMillis);
        return persistCandidate(candidate);
    }

    public synchronized CompletableFuture<Boolean> extendAsync(UUID uuid, long durationMillis, String giftNote) {
        if (uuid == null || durationMillis <= 0L || writeInFlight) return CompletableFuture.completedFuture(false);
        long base = Math.max(System.currentTimeMillis(), expiresAt(uuid));
        final long next;
        try {
            next = Math.addExact(base, durationMillis);
        } catch (ArithmeticException overflow) {
            return CompletableFuture.completedFuture(false);
        }
        YamlConfiguration candidate = copyStore();
        if (candidate == null) return CompletableFuture.completedFuture(false);
        String path = "players." + uuid;
        candidate.set(path + ".expires", next);
        if (giftNote != null && !giftNote.isBlank()) {
            candidate.set(path + ".gift-note", giftNote);
            candidate.set(path + ".gift-note-id", UUID.randomUUID().toString());
        }
        return persistCandidate(candidate);
    }

    public synchronized CompletableFuture<GiftNote> getGiftNoteAsync(UUID uuid) {
        if (uuid == null) return CompletableFuture.completedFuture(null);
        if (writeInFlight) return writeIdle.thenCompose(ignored -> getGiftNoteAsync(uuid));
        return CompletableFuture.completedFuture(pendingGiftNotes.get(uuid));
    }

    public synchronized CompletableFuture<Boolean> acknowledgeGiftNoteAsync(UUID uuid, String expectedNoteId) {
        if (uuid == null || expectedNoteId == null || expectedNoteId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        if (writeInFlight) {
            return writeIdle.thenCompose(ignored -> acknowledgeGiftNoteAsync(uuid, expectedNoteId));
        }
        GiftNote current = pendingGiftNotes.get(uuid);
        if (current == null || !current.id().equals(expectedNoteId)) {
            return CompletableFuture.completedFuture(false);
        }
        YamlConfiguration candidate = copyStore();
        if (candidate == null) return CompletableFuture.completedFuture(false);
        String path = "players." + uuid;
        candidate.set(path + ".gift-note", null);
        candidate.set(path + ".gift-note-id", null);
        return persistCandidate(candidate);
    }

    public synchronized CompletableFuture<Boolean> setGiftNoteAsync(UUID uuid, String note) {
        if (uuid == null || writeInFlight) return CompletableFuture.completedFuture(false);
        YamlConfiguration candidate = copyStore();
        if (candidate == null) return CompletableFuture.completedFuture(false);
        String path = "players." + uuid;
        if (note == null || note.isBlank()) {
            candidate.set(path + ".gift-note", null);
            candidate.set(path + ".gift-note-id", null);
        } else {
            candidate.set(path + ".gift-note", note);
            candidate.set(path + ".gift-note-id", UUID.randomUUID().toString());
        }
        return persistCandidate(candidate);
    }

    private static String legacyGiftNoteId(UUID uuid, String note) {
        return UUID.nameUUIDFromBytes((uuid + "\n" + note).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private YamlConfiguration copyStore() {
        YamlConfiguration candidate = new YamlConfiguration();
        try {
            candidate.loadFromString(store.saveToString());
            return candidate;
        } catch (InvalidConfigurationException exception) {
            LOG.log(Level.SEVERE, "PlusStorage: не удалось подготовить снимок YAML", exception);
            return null;
        }
    }

    private CompletableFuture<Boolean> persistCandidate(YamlConfiguration candidate) {
        // Все обращения к YAML и построение Java-снимка выполняются до передачи строки worker-потоку.
        final String snapshot = candidate.saveToString();
        Map<UUID, Long> nextExpires = new HashMap<>();
        Map<UUID, GiftNote> nextNotes = new HashMap<>();
        var players = candidate.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    var section = players.getConfigurationSection(key);
                    if (section == null) continue;
                    long expiry = section.getLong("expires", 0L);
                    if (expiry > 0L) nextExpires.put(uuid, expiry);
                    String note = section.getString("gift-note", null);
                    if (note != null && !note.isBlank()) {
                        String noteId = section.getString("gift-note-id", legacyGiftNoteId(uuid, note));
                        nextNotes.put(uuid, new GiftNote(noteId, note));
                    }
                } catch (IllegalArgumentException ignored) {
                    // Некорректные UUID пропускаются без потери остальных записей.
                }
            }
        }
        writeInFlight = true;
        CompletableFuture<Void> idle = new CompletableFuture<>();
        writeIdle = idle;
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        writer.submit(file, snapshot).whenComplete((saved, failure) -> {
            boolean successful = failure == null && Boolean.TRUE.equals(saved);
            synchronized (PlusStorage.this) {
                if (successful) {
                    store = candidate;
                    expires.clear();
                    expires.putAll(nextExpires);
                    pendingGiftNotes.clear();
                    pendingGiftNotes.putAll(nextNotes);
                } else if (failure != null) {
                    LOG.log(Level.SEVERE, "PlusStorage: асинхронная запись завершилась ошибкой", failure);
                }
                writeInFlight = false;
            }
            result.complete(successful);
            idle.complete(null);
        });
        return result;
    }

    /** Для selftest: число активных подписок в памяти. */
    public synchronized int activeCount(long now) {
        int count = 0;
        for (long expiry : expires.values()) if (expiry > now) count++;
        return count;
    }
}

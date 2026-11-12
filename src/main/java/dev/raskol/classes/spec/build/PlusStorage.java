// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.build;

import java.io.File;
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
    private final Map<UUID, String> pendingGiftNotes = new HashMap<>();
    private boolean writeInFlight;

    public PlusStorage(File folder, AsyncStorageWriter writer) {
        this.file = new File(folder, "plus-subscription.yml");
        this.writer = writer;
        this.store = SafeStorage.loadWithFallback(file, LOG);
        loadFromStore();
    }

    private void loadFromStore() {
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
                if (note != null && !note.isBlank()) pendingGiftNotes.put(uuid, note);
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
        if (giftNote != null && !giftNote.isBlank()) candidate.set(path + ".gift-note", giftNote);
        return persistCandidate(candidate);
    }

    public synchronized CompletableFuture<String> consumeGiftNoteAsync(UUID uuid) {
        if (uuid == null || writeInFlight) return CompletableFuture.completedFuture(null);
        String note = pendingGiftNotes.get(uuid);
        if (note == null) return CompletableFuture.completedFuture(null);
        YamlConfiguration candidate = copyStore();
        if (candidate == null) return CompletableFuture.completedFuture(null);
        candidate.set("players." + uuid + ".gift-note", null);
        return persistCandidate(candidate).thenApply(saved -> saved ? note : null);
    }

    public synchronized CompletableFuture<Boolean> setGiftNoteAsync(UUID uuid, String note) {
        if (uuid == null || writeInFlight) return CompletableFuture.completedFuture(false);
        YamlConfiguration candidate = copyStore();
        if (candidate == null) return CompletableFuture.completedFuture(false);
        candidate.set("players." + uuid + ".gift-note", note == null || note.isBlank() ? null : note);
        return persistCandidate(candidate);
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
        final String snapshot = candidate.saveToString();
        writeInFlight = true;
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        writer.submit(file, snapshot).whenComplete((saved, failure) -> {
            boolean successful = failure == null && Boolean.TRUE.equals(saved);
            synchronized (PlusStorage.this) {
                if (successful) {
                    store = candidate;
                    loadFromStore();
                } else if (failure != null) {
                    LOG.log(Level.SEVERE, "PlusStorage: асинхронная запись завершилась ошибкой", failure);
                }
                writeInFlight = false;
            }
            result.complete(successful);
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

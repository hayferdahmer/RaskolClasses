// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.build;

import dev.raskol.classes.storage.SafeStorage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/** Durable storage for raskolspec+ expirations and pending gift notifications. */
public final class PlusStorage {
    private static final Logger LOG = Logger.getLogger("RaskolClasses");
    private final File file;
    private final YamlConfiguration store;
    private final Map<UUID, Long> expires = new HashMap<>();
    private final Map<UUID, String> pendingGiftNotes = new HashMap<>();

    public PlusStorage(File folder) {
        file = new File(folder, "plus-subscription.yml");
        store = SafeStorage.loadWithFallback(file, LOG);
        loadFromStore();
    }

    private void loadFromStore() {
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
                // Ignore malformed UUIDs and keep valid records available.
            }
        }
    }

    public synchronized long expiresAt(UUID uuid) { return uuid == null ? 0L : expires.getOrDefault(uuid, 0L); }

    /** Writes first, then updates memory; a failed disk write never grants access. */
    public synchronized boolean setExpires(UUID uuid, long epochMillis) {
        if (uuid == null || epochMillis < 0L) return false;
        store.set("players." + uuid + ".expires", epochMillis == 0L ? null : epochMillis);
        if (!flush()) {
            store.set("players." + uuid + ".expires", expiresAt(uuid) == 0L ? null : expiresAt(uuid));
            return false;
        }
        if (epochMillis == 0L) expires.remove(uuid); else expires.put(uuid, epochMillis);
        return true;
    }

    /** Extends from the later of now/expiry and persists before making access active. */
    public synchronized boolean extend(UUID uuid, long durationMillis, String giftNote) {
        if (uuid == null || durationMillis <= 0L) return false;
        long now = System.currentTimeMillis();
        long base = Math.max(now, expiresAt(uuid));
        final long next;
        try { next = Math.addExact(base, durationMillis); }
        catch (ArithmeticException overflow) { return false; }

        String path = "players." + uuid;
        store.set(path + ".expires", next);
        if (giftNote != null && !giftNote.isBlank()) store.set(path + ".gift-note", giftNote);
        if (!flush()) {
            store.set(path + ".expires", expiresAt(uuid) == 0L ? null : expiresAt(uuid));
            String oldNote = pendingGiftNotes.get(uuid);
            store.set(path + ".gift-note", oldNote == null ? null : oldNote);
            return false;
        }
        expires.put(uuid, next);
        if (giftNote != null && !giftNote.isBlank()) pendingGiftNotes.put(uuid, giftNote);
        return true;
    }

    public synchronized String consumeGiftNote(UUID uuid) {
        if (uuid == null) return null;
        String note = pendingGiftNotes.get(uuid);
        if (note == null) return null;
        store.set("players." + uuid + ".gift-note", null);
        if (!flush()) {
            store.set("players." + uuid + ".gift-note", note);
            return null;
        }
        pendingGiftNotes.remove(uuid);
        return note;
    }

    public synchronized boolean setGiftNote(UUID uuid, String note) {
        if (uuid == null) return false;
        String old = pendingGiftNotes.get(uuid);
        store.set("players." + uuid + ".gift-note", note == null || note.isBlank() ? null : note);
        if (!flush()) {
            store.set("players." + uuid + ".gift-note", old);
            return false;
        }
        if (note == null || note.isBlank()) pendingGiftNotes.remove(uuid); else pendingGiftNotes.put(uuid, note);
        return true;
    }

    private boolean flush() { return SafeStorage.saveAtomic(store, file, LOG); }

    /** For selftest: number of active subscriptions in memory. */
    public int activeCount(long now) {
        int count = 0;
        for (long expiry : expires.values()) if (expiry > now) count++;
        return count;
    }
}

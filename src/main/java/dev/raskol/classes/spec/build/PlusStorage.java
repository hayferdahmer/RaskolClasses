// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.build;

import dev.raskol.classes.storage.SafeStorage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 1.14.15: YAML-хранилище подписок raskolspec+.
 *
 * Формат файла players/<uuid>.yml (или один общий plus-subscription.yml, выбираем
 * общий для простоты миграции):
 *   players:
 *     <uuid>:
 *       expires: <epochMillis>
 *       gift-note: "строка нотификации для оффлайн-игрока"
 *
 * Write-through: каждое изменение persist'ится на диск через SafeStorage.saveAtomic.
 * Чтение in-memory: Map<UUID, Long> expiry; при загрузке читается целиком.
 *
 * Оффлайн-уведомления: поле gift-note хранит текст подарка до следующего входа
 * игрока; onPlayerJoin читает и очищает поле.
 */
public final class PlusStorage {

    private static final Logger LOG = Logger.getLogger("RaskolClasses");

    private final File file;
    private final YamlConfiguration store;
    private final Map<UUID, Long> expires = new HashMap<>();
    private final Map<UUID, String> pendingGiftNotes = new HashMap<>();

    public PlusStorage(File folder) {
        this.file = new File(folder, "plus-subscription.yml");
        this.store = SafeStorage.loadWithFallback(file, LOG);
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
                if (exp > 0L) {
                    expires.put(uuid, exp);
                }
                String note = sec.getString("gift-note", null);
                if (note != null && !note.isBlank()) {
                    pendingGiftNotes.put(uuid, note);
                }
            } catch (IllegalArgumentException ignored) {
                // некорректный uuid-ключ — пропускаем
            }
        }
    }

    public long expiresAt(UUID uuid) {
        return expires.getOrDefault(uuid, 0L);
    }

    public boolean setExpires(UUID uuid, long epochMillis) {
        if (uuid == null) return false;
        long previous = expiresAt(uuid);
        if (epochMillis <= 0L) {
            expires.remove(uuid);
            store.set("players." + uuid + ".expires", null);
        } else {
            expires.put(uuid, epochMillis);
            store.set("players." + uuid + ".expires", epochMillis);
        }
        if (flush()) return true;
        if (previous > 0L) {
            expires.put(uuid, previous);
            store.set("players." + uuid + ".expires", previous);
        } else {
            expires.remove(uuid);
            store.set("players." + uuid + ".expires", null);
        }
        return false;
    }

    public String consumeGiftNote(UUID uuid) {
        String note = pendingGiftNotes.remove(uuid);
        if (note != null) {
            store.set("players." + uuid + ".gift-note", null);
            flush();
        }
        return note;
    }

    public void setGiftNote(UUID uuid, String note) {
        if (note == null || note.isBlank()) {
            pendingGiftNotes.remove(uuid);
            store.set("players." + uuid + ".gift-note", null);
        } else {
            pendingGiftNotes.put(uuid, note);
            store.set("players." + uuid + ".gift-note", note);
        }
        flush();
    }

    private boolean flush() {
        return SafeStorage.saveAtomicResult(store, file, LOG);
    }

    /** Для selftest: число активных подписок в памяти. */
    public int activeCount(long now) {
        int n = 0;
        for (long e : expires.values()) {
            if (e > now) n++;
        }
        return n;
    }
}


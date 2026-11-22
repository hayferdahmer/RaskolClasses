// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.character.profile;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import dev.raskol.classes.storage.AsyncStorageWriter;
import dev.raskol.classes.storage.SafeStorage;

/**
 * Единый YAML-файл профилей персонажей с атомарной асинхронной записью.
 *
 * Все чтения и изменения профилей, а также сериализация выполняются на
 * вызывающем потоке. В StorageWriter передаётся только готовый YAML-снимок.
 */
public final class CharacterProfileStorage {

    public static final String FILE_NAME = "characters.yml";
    private static final String PROFILES_PATH = "profiles";
    private static final String SCHEMA_PATH = "schema-version";

    private final File file;
    private final AsyncStorageWriter writer;
    private final Logger logger;
    private final LongSupplier clock;
    private final Map<UUID, CharacterProfile> profiles = new LinkedHashMap<>();
    private final AtomicLong mutationVersion = new AtomicLong();
    private final AtomicLong persistedVersion = new AtomicLong();

    public CharacterProfileStorage(File file, AsyncStorageWriter writer, Logger logger) {
        this(file, writer, logger, System::currentTimeMillis);
    }

    CharacterProfileStorage(File file, AsyncStorageWriter writer, Logger logger, LongSupplier clock) {
        this.file = Objects.requireNonNull(file, "file");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
        load();
    }

    private void load() {
        YamlConfiguration yaml = SafeStorage.loadWithFallback(file, logger);
        int schemaVersion = yaml.getInt(SCHEMA_PATH, 0);
        if (schemaVersion < 0 || schemaVersion > CharacterProfile.CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("Неподдерживаемая версия characters.yml: " + schemaVersion);
        }

        boolean migrated = schemaVersion < CharacterProfile.CURRENT_SCHEMA_VERSION;
        ConfigurationSection section = yaml.getConfigurationSection(PROFILES_PATH);
        if (section != null) {
            for (String rawId : section.getKeys(false)) {
                UUID playerId;
                try {
                    playerId = UUID.fromString(rawId);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException("Некорректный UUID профиля в characters.yml: " + rawId, exception);
                }

                ConfigurationSection profileSection = section.getConfigurationSection(rawId);
                if (profileSection == null) {
                    throw new IllegalStateException("Профиль " + rawId + " должен быть YAML-разделом");
                }
                CharacterProfile profile = readProfile(playerId, profileSection, migrated);
                profiles.put(playerId, profile);
            }
        }

        if (migrated && file.exists()) {
            mutationVersion.incrementAndGet();
            logger.info("CharacterProfileStorage: characters.yml будет обновлён до схемы "
                    + CharacterProfile.CURRENT_SCHEMA_VERSION);
        }
    }

    private CharacterProfile readProfile(UUID playerId, ConfigurationSection section, boolean allowLegacyDefaults) {
        int profileSchema = section.getInt("schema-version", allowLegacyDefaults ? 0 : -1);
        if (profileSchema < 0 || profileSchema > CharacterProfile.CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("Неподдерживаемая схема профиля " + playerId + ": " + profileSchema);
        }

        boolean legacy = profileSchema < CharacterProfile.CURRENT_SCHEMA_VERSION;
        int level = readInt(section, "level", legacy, CharacterProfile.MIN_LEVEL);
        long experience = readLong(section, "experience", legacy, 0L);
        long createdAt = readLong(section, "created-at", legacy, clock.getAsLong());
        long updatedAt = readLong(section, "updated-at", legacy, createdAt);

        try {
            return new CharacterProfile(
                    playerId,
                    CharacterProfile.CURRENT_SCHEMA_VERSION,
                    level,
                    experience,
                    createdAt,
                    updatedAt);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Некорректные данные профиля " + playerId, exception);
        }
    }

    private static int readInt(ConfigurationSection section, String key, boolean allowDefault, int defaultValue) {
        if (!section.contains(key)) {
            if (allowDefault) {
                return defaultValue;
            }
            throw new IllegalStateException("В профиле отсутствует обязательное поле " + key);
        }
        return section.getInt(key, Integer.MIN_VALUE);
    }

    private static long readLong(ConfigurationSection section, String key, boolean allowDefault, long defaultValue) {
        if (!section.contains(key)) {
            if (allowDefault) {
                return defaultValue;
            }
            throw new IllegalStateException("В профиле отсутствует обязательное поле " + key);
        }
        return section.getLong(key, Long.MIN_VALUE);
    }

    /** Возвращает профиль или создаёт новый профиль персонажа первого уровня. */
    public synchronized CharacterProfile ensureProfile(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        CharacterProfile existing = profiles.get(playerId);
        if (existing != null) {
            return existing;
        }
        CharacterProfile created = CharacterProfile.newCharacter(playerId, clock.getAsLong());
        profiles.put(playerId, created);
        markDirty();
        return created;
    }

    public synchronized Optional<CharacterProfile> find(UUID playerId) {
        return Optional.ofNullable(profiles.get(Objects.requireNonNull(playerId, "playerId")));
    }

    /** Заменяет профиль целиком; профиль другого UUID или неизвестной схемы не принимается. */
    public synchronized void upsert(CharacterProfile profile) {
        Objects.requireNonNull(profile, "profile");
        if (profile.schemaVersion() != CharacterProfile.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Неподдерживаемая версия профиля");
        }
        profiles.put(profile.playerId(), profile);
        markDirty();
    }

    public synchronized int profileCount() {
        return profiles.size();
    }

    private void markDirty() {
        mutationVersion.incrementAndGet();
    }

    /** Сериализует снимок на вызывающем потоке и передаёт строку фоновому writer. */
    public synchronized boolean save() {
        if (!isDirty()) {
            return false;
        }

        long snapshotVersion = mutationVersion.get();
        String snapshot = serialize().saveToString();
        var future = writer.submit(file, snapshot);
        future.thenAccept(successful -> {
            if (successful) {
                persistedVersion.accumulateAndGet(snapshotVersion, Math::max);
            } else {
                logger.severe("CharacterProfileStorage: не удалось сохранить поколение " + snapshotVersion);
            }
        });
        return !future.isDone() || future.getNow(false);
    }

    public synchronized boolean saveIfDirty() {
        return isDirty() && save();
    }

    public boolean isDirty() {
        return mutationVersion.get() > persistedVersion.get();
    }

    private YamlConfiguration serialize() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(SCHEMA_PATH, CharacterProfile.CURRENT_SCHEMA_VERSION);
        for (CharacterProfile profile : profiles.values()) {
            String base = PROFILES_PATH + "." + profile.playerId();
            yaml.set(base + ".schema-version", profile.schemaVersion());
            yaml.set(base + ".level", profile.level());
            yaml.set(base + ".experience", profile.experienceIntoLevel());
            yaml.set(base + ".created-at", profile.createdAtMillis());
            yaml.set(base + ".updated-at", profile.updatedAtMillis());
        }
        return yaml;
    }
}

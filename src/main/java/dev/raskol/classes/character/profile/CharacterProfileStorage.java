// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.character.profile;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private static final int CURRENT_STORAGE_SCHEMA_VERSION = 2;
    private static final String PROFILES_PATH = "profiles";
    private static final String QUEST_REWARDS_PATH = "processed-quest-rewards";
    private static final String SCHEMA_PATH = "schema-version";

    private final File file;
    private final AsyncStorageWriter writer;
    private final Logger logger;
    private final LongSupplier clock;
    private final Map<UUID, CharacterProfile> profiles = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> processedQuestRewards = new LinkedHashMap<>();
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
        int schemaVersion = readInteger(yaml, SCHEMA_PATH, 0);
        if (schemaVersion < 0 || schemaVersion > CURRENT_STORAGE_SCHEMA_VERSION) {
            throw new IllegalStateException("Неподдерживаемая версия characters.yml: " + schemaVersion);
        }

        boolean storageMigrated = schemaVersion < CURRENT_STORAGE_SCHEMA_VERSION;
        boolean allowLegacyProfiles = schemaVersion == 0;
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
                CharacterProfile profile = readProfile(playerId, profileSection, allowLegacyProfiles);
                profiles.put(playerId, profile);
            }
        }

        loadProcessedQuestRewards(yaml);

        if (storageMigrated && file.exists()) {
            mutationVersion.incrementAndGet();
            logger.info("CharacterProfileStorage: characters.yml будет обновлён до схемы "
                    + CURRENT_STORAGE_SCHEMA_VERSION);
        }
    }

    private void loadProcessedQuestRewards(YamlConfiguration yaml) {
        if (!yaml.contains(QUEST_REWARDS_PATH)) {
            return;
        }
        ConfigurationSection section = yaml.getConfigurationSection(QUEST_REWARDS_PATH);
        if (section == null) {
            throw new IllegalStateException("Журнал квестовых наград должен быть YAML-разделом");
        }
        for (String rawId : section.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(rawId);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Некорректный UUID в журнале квестовых наград: " + rawId, exception);
            }

            if (!profiles.containsKey(playerId)) {
                throw new IllegalStateException("Журнал квестовых наград ссылается на отсутствующий профиль " + rawId);
            }
            Object rawRewards = section.get(rawId);
            if (!(rawRewards instanceof List<?> rewards)) {
                throw new IllegalStateException("Журнал квестовых наград игрока " + rawId + " должен быть списком");
            }
            Set<String> parsedRewards = new LinkedHashSet<>();
            for (Object rawReward : rewards) {
                if (!(rawReward instanceof String rewardId) || !isValidRewardId(rewardId)) {
                    throw new IllegalStateException("Некорректный идентификатор квестовой награды игрока " + rawId);
                }
                parsedRewards.add(rewardId);
            }
            processedQuestRewards.put(playerId, parsedRewards);
        }
    }

    private static boolean isValidRewardId(String rewardId) {
        if (rewardId == null || rewardId.isBlank() || rewardId.length() > 160) {
            return false;
        }
        return rewardId.chars().noneMatch(Character::isISOControl);
    }

    private CharacterProfile readProfile(UUID playerId, ConfigurationSection section, boolean allowLegacyDefaults) {
        int profileSchema = readInteger(section, "schema-version", allowLegacyDefaults ? 0 : -1);
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
                    playerId, CharacterProfile.CURRENT_SCHEMA_VERSION, level, experience, createdAt, updatedAt);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Некорректные данные профиля " + playerId, exception);
        }
    }

    private static int readInteger(ConfigurationSection section, String key, int defaultValue) {
        if (!section.contains(key)) {
            return defaultValue;
        }
        Object value = section.get(key);
        if (!(value instanceof Number number)) {
            return Integer.MIN_VALUE;
        }
        int parsed = number.intValue();
        if (number.doubleValue() != (double) parsed) {
            return Integer.MIN_VALUE;
        }
        return parsed;
    }

    private static int readInt(ConfigurationSection section, String key, boolean allowDefault, int defaultValue) {
        if (!section.contains(key)) {
            if (allowDefault) {
                return defaultValue;
            }
            throw new IllegalStateException("В профиле отсутствует обязательное поле " + key);
        }
        return readInteger(section, key, Integer.MIN_VALUE);
    }

    private static long readLong(ConfigurationSection section, String key, boolean allowDefault, long defaultValue) {
        if (!section.contains(key)) {
            if (allowDefault) {
                return defaultValue;
            }
            throw new IllegalStateException("В профиле отсутствует обязательное поле " + key);
        }
        Object value = section.get(key);
        if (!(value instanceof Number number)) {
            return Long.MIN_VALUE;
        }
        long parsed = number.longValue();
        if (number.doubleValue() != (double) parsed) {
            return Long.MIN_VALUE;
        }
        return parsed;
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

    /**
     * Атомарно сохраняет обновлённый профиль вместе с квитанцией о выдаче квестовой награды.
     * Оба изменения попадают в один YAML-снимок, поэтому повторная выдача после рестарта невозможна.
     *
     * @return true, если квитанция записана впервые; false, если награда уже была обработана
     */
    public synchronized boolean recordQuestRewardIfAbsent(
            UUID playerId, String rewardId, CharacterProfile updatedProfile) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(updatedProfile, "updatedProfile");
        if (!playerId.equals(updatedProfile.playerId())) {
            throw new IllegalArgumentException("Профиль квестовой награды принадлежит другому игроку");
        }
        if (!isValidRewardId(rewardId)) {
            throw new IllegalArgumentException("Некорректный идентификатор квестовой награды");
        }
        if (!profiles.containsKey(playerId)) {
            throw new IllegalStateException("Перед записью квестовой награды профиль игрока должен существовать");
        }

        Set<String> rewards = processedQuestRewards.computeIfAbsent(playerId, ignored -> new LinkedHashSet<>());
        if (!rewards.add(rewardId)) {
            return false;
        }
        profiles.put(playerId, updatedProfile);
        markDirty();
        return true;
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
        yaml.set(SCHEMA_PATH, CURRENT_STORAGE_SCHEMA_VERSION);
        for (CharacterProfile profile : profiles.values()) {
            String base = PROFILES_PATH + "." + profile.playerId();
            yaml.set(base + ".schema-version", profile.schemaVersion());
            yaml.set(base + ".level", profile.level());
            yaml.set(base + ".experience", profile.experienceIntoLevel());
            yaml.set(base + ".created-at", profile.createdAtMillis());
            yaml.set(base + ".updated-at", profile.updatedAtMillis());
        }
        for (Map.Entry<UUID, Set<String>> entry : processedQuestRewards.entrySet()) {
            yaml.set(QUEST_REWARDS_PATH + "." + entry.getKey(), List.copyOf(entry.getValue()));
        }
        return yaml;
    }
}

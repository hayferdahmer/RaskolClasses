// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.character.profile;

import java.io.File;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    private static final int CURRENT_STORAGE_SCHEMA_VERSION = 3;
    private static final String PROFILES_PATH = "profiles";
    private static final String QUEST_REWARDS_PATH = "processed-quest-rewards";
    private static final String SCHEMA_PATH = "schema-version";

    private final File file;
    private final AsyncStorageWriter writer;
    private final Logger logger;
    private final LongSupplier clock;
    private final Map<UUID, CharacterProfile> profiles = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> processedQuestRewards = new LinkedHashMap<>();
    private final Map<UUID, String> specializationMains = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Map<String, Integer>>> specializationRanks = new LinkedHashMap<>();
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
                loadSpecialization(playerId, profileSection);
            }
        }

        loadProcessedQuestRewards(yaml);

        if (storageMigrated && file.exists()) {
            mutationVersion.incrementAndGet();
            logger.info("CharacterProfileStorage: characters.yml будет обновлён до схемы "
                    + CURRENT_STORAGE_SCHEMA_VERSION);
        }
    }

    private void loadSpecialization(UUID playerId, ConfigurationSection profileSection) {
        if (!profileSection.contains("specialization")) {
            return;
        }
        ConfigurationSection specialization = profileSection.getConfigurationSection("specialization");
        if (specialization == null) {
            throw new IllegalStateException(
                    "Специализация профиля " + playerId + " должна быть YAML-разделом");
        }

        if (specialization.contains("main")) {
            Object rawMain = specialization.get("main");
            if (!(rawMain instanceof String main) || !isValidStorageKey(main)) {
                throw new IllegalStateException("Некорректная основная специализация профиля " + playerId);
            }
            specializationMains.put(playerId, main);
        }

        if (!specialization.contains("ranks")) {
            return;
        }
        ConfigurationSection ranks = specialization.getConfigurationSection("ranks");
        if (ranks == null) {
            throw new IllegalStateException(
                    "Ранги специализации профиля " + playerId + " должны быть YAML-разделом");
        }

        Map<String, Map<String, Integer>> parsedTrees = new LinkedHashMap<>();
        for (String treeId : ranks.getKeys(false)) {
            if (!isValidStorageKey(treeId)) {
                throw new IllegalStateException("Некорректный идентификатор дерева специализации " + treeId);
            }
            ConfigurationSection tree = ranks.getConfigurationSection(treeId);
            if (tree == null) {
                throw new IllegalStateException(
                        "Дерево специализации " + treeId + " должно быть YAML-разделом");
            }
            Map<String, Integer> parsedRanks = new LinkedHashMap<>();
            for (String nodeId : tree.getKeys(false)) {
                Object rawRank = tree.get(nodeId);
                if (!isValidStorageKey(nodeId)
                        || !(rawRank instanceof Number number)
                        || number.doubleValue() != (double) number.intValue()
                        || number.intValue() <= 0) {
                    throw new IllegalStateException(
                            "Некорректный ранг узла " + nodeId + " дерева " + treeId
                                    + " профиля " + playerId);
                }
                parsedRanks.put(nodeId, number.intValue());
            }
            if (!parsedRanks.isEmpty()) {
                parsedTrees.put(treeId, parsedRanks);
            }
        }
        if (!parsedTrees.isEmpty()) {
            specializationRanks.put(playerId, parsedTrees);
        }
    }

    private static boolean isValidStorageKey(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,64}");
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

    /** Проверяет, была ли уже обработана указанная квестовая награда для игрока. */
    public synchronized boolean hasProcessedQuestReward(UUID playerId, String rewardId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!isValidRewardId(rewardId)) {
            throw new IllegalArgumentException("Некорректный идентификатор квестовой награды");
        }
        return processedQuestRewards.getOrDefault(playerId, Set.of()).contains(rewardId);
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

    /** Основная специализация персонажа внутри единого characters.yml. */
    public synchronized String getSpecializationMain(UUID playerId) {
        return specializationMains.get(Objects.requireNonNull(playerId, "playerId"));
    }

    public synchronized void setSpecializationMain(UUID playerId, String specId) {
        Objects.requireNonNull(playerId, "playerId");
        if (specId != null && !isValidStorageKey(specId)) {
            throw new IllegalArgumentException("Некорректный идентификатор специализации");
        }
        if (specId == null && !profiles.containsKey(playerId)) {
            return;
        }
        if (specId != null) {
            ensureProfile(playerId);
        }
        if (Objects.equals(specializationMains.get(playerId), specId)) {
            return;
        }
        if (specId == null) {
            specializationMains.remove(playerId);
        } else {
            specializationMains.put(playerId, specId);
        }
        markDirty();
    }

    public synchronized Map<String, Integer> getSpecializationRanks(UUID playerId, String treeId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!isValidStorageKey(treeId)) {
            throw new IllegalArgumentException("Некорректный идентификатор дерева специализации");
        }
        Map<String, Map<String, Integer>> trees = specializationRanks.get(playerId);
        if (trees == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Integer> ranks = trees.get(treeId);
        return ranks == null ? new LinkedHashMap<>() : new LinkedHashMap<>(ranks);
    }

    public synchronized void setSpecializationRanks(
            UUID playerId, String treeId, Map<String, Integer> ranks) {
        Objects.requireNonNull(playerId, "playerId");
        if (!isValidStorageKey(treeId)) {
            throw new IllegalArgumentException("Некорректный идентификатор дерева специализации");
        }
        Map<String, Integer> cleanRanks = new LinkedHashMap<>();
        if (ranks != null) {
            for (Map.Entry<String, Integer> entry : ranks.entrySet()) {
                if (!isValidStorageKey(entry.getKey())) {
                    throw new IllegalArgumentException("Некорректный идентификатор узла специализации");
                }
                Integer rank = entry.getValue();
                if (rank != null && rank > 0) {
                    cleanRanks.put(entry.getKey(), rank);
                }
            }
        }

        Map<String, Map<String, Integer>> existingTrees = specializationRanks.get(playerId);
        if (cleanRanks.isEmpty()) {
            if (existingTrees != null && existingTrees.remove(treeId) != null) {
                if (existingTrees.isEmpty()) {
                    specializationRanks.remove(playerId);
                }
                markDirty();
            }
            return;
        }

        ensureProfile(playerId);
        specializationRanks.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>())
                .put(treeId, cleanRanks);
        markDirty();
    }

    public synchronized List<String> storedSpecializationTreeIds(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Map<String, Map<String, Integer>> trees = specializationRanks.get(playerId);
        return trees == null ? List.of() : List.copyOf(trees.keySet());
    }

    public synchronized void removeSpecializationTree(UUID playerId, String treeId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!isValidStorageKey(treeId)) {
            throw new IllegalArgumentException("Некорректный идентификатор дерева специализации");
        }
        Map<String, Map<String, Integer>> trees = specializationRanks.get(playerId);
        if (trees != null && trees.remove(treeId) != null) {
            if (trees.isEmpty()) {
                specializationRanks.remove(playerId);
            }
            markDirty();
        }
    }

    public synchronized boolean hasSpecializationData(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return specializationMains.containsKey(playerId) || specializationRanks.containsKey(playerId);
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

        CompletableFuture<Boolean> future = submitSnapshot();
        return !future.isDone() || future.getNow(false);
    }

    private CompletableFuture<Boolean> submitSnapshot() {
        long snapshotVersion = mutationVersion.get();
        String snapshot = serialize().saveToString();
        CompletableFuture<Boolean> future = writer.submit(file, snapshot);
        future.thenAccept(successful -> {
            if (successful) {
                persistedVersion.accumulateAndGet(snapshotVersion, Math::max);
            } else {
                logger.severe("CharacterProfileStorage: не удалось сохранить поколение " + snapshotVersion);
            }
        });
        return future;
    }

    public synchronized boolean saveIfDirty() {
        return isDirty() && save();
    }

    /**
     * Сохраняет единый YAML-снимок и ждёт подтверждения записи.
     * Используется только при миграции, когда старый файл нельзя архивировать
     * до успешной фиксации всех данных в characters.yml.
     */
    public synchronized boolean saveAndAwait(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout не может быть отрицательным");
        }
        if (!isDirty()) {
            return true;
        }
        CompletableFuture<Boolean> future = submitSnapshot();
        try {
            boolean successful = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (successful) {
                persistedVersion.accumulateAndGet(mutationVersion.get(), Math::max);
            }
            return successful;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.severe("CharacterProfileStorage: ожидание миграционного сохранения прервано");
            return false;
        } catch (ExecutionException | TimeoutException exception) {
            logger.severe("CharacterProfileStorage: не удалось подтвердить миграционное сохранение: "
                    + exception.getMessage());
            return false;
        }
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

            String mainSpec = specializationMains.get(profile.playerId());
            if (mainSpec != null) {
                yaml.set(base + ".specialization.main", mainSpec);
            }
            Map<String, Map<String, Integer>> trees = specializationRanks.get(profile.playerId());
            if (trees != null) {
                for (Map.Entry<String, Map<String, Integer>> tree : trees.entrySet()) {
                    for (Map.Entry<String, Integer> rank : tree.getValue().entrySet()) {
                        yaml.set(
                                base + ".specialization.ranks." + tree.getKey() + "." + rank.getKey(), rank.getValue());
                    }
                }
            }
        }
        for (Map.Entry<UUID, Set<String>> entry : processedQuestRewards.entrySet()) {
            yaml.set(QUEST_REWARDS_PATH + "." + entry.getKey(), List.copyOf(entry.getValue()));
        }
        return yaml;
    }
}

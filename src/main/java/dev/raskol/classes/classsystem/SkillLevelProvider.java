// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.classsystem;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Уровень профильного скилла AuraSkills — через отражение.
 * V3: единственная разрешённая рефлексия в плагине, graceful degrade:
 * нет плагина или API изменился → NO_SKILL_SYSTEM, анлоки сняты.
 * Кэш уровней UUID → (скилл → уровень), TTL 1 с (O3).
 */
public final class SkillLevelProvider {

    /** AuraSkills отсутствует — уровневые требования отключены. */
    public static final int NO_SKILL_SYSTEM = -1;

    private static final long CACHE_TTL_MILLIS = 1_000L;

    private record CacheEntry(int level, long expiresAt) {
    }

    private final RaskolClasses plugin;
    private final Map<UUID, Map<String, CacheEntry>> cache = new ConcurrentHashMap<>();
    private final Map<String, Object> skillConstants = new ConcurrentHashMap<>();

    private Object api;
    private Method getUser;
    private Method getSkillLevel;
    private boolean available;

    public SkillLevelProvider(RaskolClasses plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().getPlugin("AuraSkills") == null) {
            plugin.getLogger().info("AuraSkills не найден: анлоки способностей не зависят от уровня");
            return;
        }
        bind();
    }

    private void bind() {
        try {
            Class<?> apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            this.api = apiClass.getMethod("get").invoke(null);
            this.getUser = apiClass.getMethod("getUser", UUID.class);
            Class<?> skill = Class.forName("dev.aurelium.auraskills.api.skill.Skill");
            this.getSkillLevel = Class.forName("dev.aurelium.auraskills.api.user.SkillsUser")
                    .getMethod("getSkillLevel", skill);
            this.available = true;
            plugin.getLogger().info("AuraSkills: хук уровней скиллов активен");
        } catch (ReflectiveOperationException | LinkageError e) {
            this.available = false;
            plugin.getLogger().warning("AuraSkills API недоступен (" + e + "): анлоки без уровня");
        }
    }

    public boolean isAuraSkillsAvailable() {
        return available;
    }

    /**
     * Уровень скилла по имени: fighting/archery/healing/sorcery/agility/defense/alchemy.
     * NO_SKILL_SYSTEM — если AuraSkills отсутствует.
     */
    public int getLevel(UUID playerId, String skillName) {
        if (!available) {
            return NO_SKILL_SYSTEM;
        }
        Map<String, CacheEntry> bySkill =
                cache.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
        CacheEntry entry = bySkill.get(skillName);
        if (entry != null && entry.expiresAt() > System.currentTimeMillis()) {
            return entry.level();
        }
        int level = fetchLevel(playerId, skillName);
        bySkill.put(skillName, new CacheEntry(level, System.currentTimeMillis() + CACHE_TTL_MILLIS));
        return level;
    }

    private int fetchLevel(UUID playerId, String skillName) {
        try {
            Object skill = skillConstants.get(skillName);
            if (skill == null) {
                skill = resolveConstant(skillName.toUpperCase());
                if (skill == null) {
                    return NO_SKILL_SYSTEM;
                }
                skillConstants.put(skillName, skill);
            }
            Object user = getUser.invoke(api, playerId);
            if (user == null) {
                return 0;
            }
            Object level = getSkillLevel.invoke(user, skill);
            return level instanceof Integer value ? value : 0;
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "AuraSkills: не удалось получить уровень", e);
            return NO_SKILL_SYSTEM;
        }
    }

    private Object resolveConstant(String enumName) {
        try {
            return Class.forName("dev.aurelium.auraskills.api.skill.Skills")
                    .getField(enumName).get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
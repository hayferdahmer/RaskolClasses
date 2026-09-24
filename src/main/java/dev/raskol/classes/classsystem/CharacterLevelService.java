// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.classsystem;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.8.0: УРОВЕНЬ ПЕРСОНАЖА = среднее (floor) топ-N скиллов AuraSkills,
 * зажатое в [0, attributes.level-cap].
 *
 * 1.9.3 FIX: фолбэк когда AuraSkills отсутствует/не отвечает —
 * character-level.fallback (дефолт 40), а НЕ player.getLevel() (ванильный XP,
 * часто 0). Раньше у игроков без AuraSkills level=0 → HP = 100 + STR×20,
 * что давало воину всего ~1000 HP вместо расчётных 2500+.
 */
public final class CharacterLevelService {

    private static final long CACHE_TTL_MILLIS = 1_000L;

    private static final List<String> DEFAULT_SKILLS = List.of(
            "fighting", "defense", "archery", "agility", "healing",
            "sorcery", "alchemy", "enchanting", "mining", "forging");

    private record CacheEntry(int level, long expiresAt) {
    }

    private final RaskolClasses plugin;
    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

    public CharacterLevelService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public int characterLevel(UUID uuid) {
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(uuid);
        if (entry != null && entry.expiresAt() > now) {
            return entry.level();
        }
        int level = compute(uuid);
        cache.put(uuid, new CacheEntry(level, now + CACHE_TTL_MILLIS));
        return level;
    }

    private int compute(UUID uuid) {
        SkillLevelProvider skills = plugin.getSkillLevels();
        List<String> names = plugin.getConfig().getStringList("character-level.skills");
        if (names.isEmpty()) {
            names = DEFAULT_SKILLS;
        }
        int n = Math.max(1, plugin.getConfig().getInt("character-level.top-n", 5));

        List<Integer> levels = new ArrayList<>(names.size());
        boolean anySystem = false;
        for (String name : names) {
            int lv = skills.getLevel(uuid, name);
            if (lv == SkillLevelProvider.NO_SKILL_SYSTEM) {
                continue;
            }
            anySystem = true;
            levels.add(Math.max(0, lv));
        }
        // 1.9.3 FIX: фолбэк = character-level.fallback, а НЕ player.getLevel()
        if (!anySystem) {
            int fallback = (int) plugin.getConfig().getDouble("character-level.fallback", 40.0);
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player == null) {
                return Math.max(0, Math.min(cap(), fallback));
            }
            String src = plugin.getConfig().getString("attributes.level-source", "character");
            if ("vanilla".equalsIgnoreCase(src)) {
                return Math.max(0, Math.min(cap(), player.getLevel()));
            }
            return Math.max(0, Math.min(cap(), fallback));
        }
        while (levels.size() < names.size()) {
            levels.add(0);
        }
        int[] arr = new int[levels.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = levels.get(i);
        }
        int raw = topNAverage(arr, n);
        return Math.max(0, Math.min(cap(), raw));
    }

    private int cap() {
        return (int) plugin.getConfig().getDouble("attributes.level-cap", 60.0);
    }

    public static int topNAverage(int[] levels, int n) {
        if (levels == null || levels.length == 0 || n <= 0) {
            return 0;
        }
        int[] sorted = levels.clone();
        Arrays.sort(sorted);
        int take = Math.min(n, sorted.length);
        long sum = 0;
        for (int i = sorted.length - take; i < sorted.length; i++) {
            sum += sorted[i];
        }
        return (int) (sum / take);
    }

    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    public void purgeStale() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry ->
                entry.getValue().expiresAt() <= now && org.bukkit.Bukkit.getPlayer(entry.getKey()) == null);
    }
}

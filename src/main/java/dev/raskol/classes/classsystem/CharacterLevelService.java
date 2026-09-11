// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.classsystem;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.8.0: УРОВЕНЬ ПЕРСОНАЖА = среднее (floor) топ-N скиллов AuraSkills,
 * зажатое в [0, attributes.level-cap]. Прогрессия привязана к ШИРИНЕ прокачки:
 * одиночное дерево до 99 больше не раздувает атрибуты (кейс 1.7.x: жрец с
 * healing 99 имел INT 130.8 / HP 992 / dodge 30% без единой вещи).
 *
 * Источник списка скиллов: character-level.skills (дефолт — 10 деревьев билда).
 * Не тронутые игроком деревья учитываются как 0 (топ-N честно падает у новичков).
 * Кэш 1 с на игрока (паттерн SkillLevelProvider).
 *
 * Фолбэки:
 *  - AuraSkills отсутствует вообще → vanilla-уровень игрока (или character-level.fallback
 *    для оффлайна/консоли);
 *  - attributes.level-source = class-skill / vanilla в AttributeService откатывает
 *    систему целиком (рубильник отката 1.7.x-поведения).
 */
public final class CharacterLevelService {

    private static final long CACHE_TTL_MILLIS = 1_000L;

    /** Дефолтный список деревьев билда (совпадает с Этапом 2 сервера). */
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

    /** Сводный уровень персонажа (кэш 1 с). */
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
                continue; // дерево/система не отвечает — пропускаем
            }
            anySystem = true;
            levels.add(Math.max(0, lv));
        }
        if (!anySystem) {
            // AuraSkills нет: vanilla-уровень игрока или конфиг-фолбэк
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                return Math.max(0, player.getLevel());
            }
            return Math.max(0, plugin.getConfig().getInt("character-level.fallback", 40));
        }
        // не тронутые деревья = 0: топ-N у новичка честно низкий
        while (levels.size() < names.size()) {
            levels.add(0);
        }
        int[] arr = new int[levels.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = levels.get(i);
        }
        int raw = topNAverage(arr, n);
        int cap = (int) plugin.getConfig().getDouble("attributes.level-cap", 60.0);
        return Math.max(0, Math.min(cap, raw));
    }

    /**
     * Pure: среднее (floor) топ-N значений; N > length → среднее всех.
     * Тот же код вызывает /rc selftest (чеки 19–20) — тест и рантайм идентичны.
     */
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

    /** Сброс кэша (смена класса/респец/quit). */
    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    /** Чистка кэша оффлайна (purge-таск). */
    public void purgeStale() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry ->
                entry.getValue().expiresAt() <= now && Bukkit.getPlayer(entry.getKey()) == null);
    }
}

// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.spec.Spec;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlaceholderAPI: %raskolclasses_*% (README, раздел VII).
 * 1.14.0 (Б8): spec/talent_points/talents читаются из spec2-слоя;
 * legacy SpecService/TalentService не используются.
 * 1.14.12 (Sprint 8, Волна 12C, P0-5-07): TTL-кэш результатов ~1 с per-player+param —
 * hot-путь TAB/hologram вызывает onPlaceholderRequest десятки раз в секунду;
 * без кэша каждый запрос делает 3–5 вызовов в сервисы (AttributeService,
 * Spec2Service, HpBarService). Кэш инвалидируется автоматически по TTL;
 * reconcile/levelup обновят значения в следующем окне.
 */
public final class RaskolPlaceholder extends PlaceholderExpansion {

    /** 1.14.12 (Волна 12C, 5-07): TTL кэша плейсхолдеров (мс). */
    private static final long CACHE_TTL_MS = 1000L;

    private record CacheEntry(String value, long expiresAt) {
    }

    private final RaskolClasses plugin;
    /** 1.14.12 (Волна 12C, 5-07): кэш результатов per-player+param. */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public RaskolPlaceholder(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "raskolclasses";
    }

    @Override
    public String getAuthor() {
        return "hayferdahmer";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    /** 1.14.12 (Волна 12C, 5-07): чистка кэша при выгрузке плагина. */
    public void invalidateAll() {
        cache.clear();
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || params == null) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        String key = uuid + ":" + params.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(key);
        if (entry != null && entry.expiresAt() > now) {
            return entry.value();
        }
        String result = compute(player, params.toLowerCase(Locale.ROOT));
        cache.put(key, new CacheEntry(result, now + CACHE_TTL_MS));
        return result;
    }

    private String compute(Player player, String params) {
        var uuid = player.getUniqueId();
        switch (params) {
            case "class": {
                PlayerClass pc = plugin.getClassProvider().getClassOf(player);
                return pc == null ? "" : pc.getDisplayName();
            }
            case "level":
                return String.valueOf(plugin.getCharacterLevels().characterLevel(uuid));
            case "hp":
                return String.valueOf((int) plugin.getHpBarService().currentFormulaHp(player));
            case "hp_max":
                return String.valueOf((int) plugin.getHpBarService().formulaMaxHp(uuid));
            case "resource":
                return String.valueOf((int) plugin.getResources().getValue(uuid));
            case "phys_resist":
                return String.valueOf((int) plugin.getResists().physicalResist(uuid));
            case "magic_resist":
                return String.valueOf((int) plugin.getResists().magicResist(uuid));
            case "dodge":
                return fmt1(plugin.getAttributes().effectiveAvoidance(uuid)[0]);
            case "parry":
                return fmt1(plugin.getAttributes().effectiveAvoidance(uuid)[1]);
            case "crit_melee":
                return fmt1(plugin.getAttributes().critMeleeChance(uuid));
            case "crit_spell":
                return fmt1(plugin.getAttributes().critSpellChance(uuid));
            case "spec": {
                String main = plugin.getSpec2Service().mainSpec(uuid);
                Spec spec = Spec.fromId(main);
                return spec == null ? "" : spec.displayName();
            }
            case "talent_points":
                return String.valueOf(plugin.getSpec2Service().availablePoints(uuid));
            case "talents":
                return plugin.getSpec2Service().spentGlobal(uuid) + "/"
                        + plugin.getSpec2Service().earnedPoints(uuid);
            default:
                return null;
        }
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}

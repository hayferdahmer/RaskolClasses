// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.config;

import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 1.11.4 (P5): кэш значений, прочитанных через KitConfigLoader.
 * Сбрасывается полностью на каждом reload (/rc reload или стартап).
 *
 * Типовые вызовы:
 *   cache.getD(PlayerClass.WARLOCK, "recoil.percent", 6.66);
 *   cache.getI(PlayerClass.WARRIOR, "passives.execute_passive.cooldown-seconds", 6);
 *
 * Поддерживает dotted-пути через ConfigurationSection.get.
 */
public final class KitConfigCache {

    private final KitConfigLoader loader;
    private final Map<String, Double> doubles = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Integer> ints = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Boolean> bools = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, String> strings = new java.util.concurrent.ConcurrentHashMap<>();

    public KitConfigCache(KitConfigLoader loader) {
        this.loader = loader;
    }

    /** Полный сброс (вызывает KitConfigLoader.reload() перед инвалидацией). */
    public void invalidate() {
        doubles.clear();
        ints.clear();
        bools.clear();
        strings.clear();
    }

    public double getD(PlayerClass pc, String path, double def) {
        String key = pc.name() + "::" + path;
        Double cached = doubles.get(key);
        if (cached != null) {
            return cached;
        }
        double v = readD(pc, path, def);
        doubles.put(key, v);
        return v;
    }

    public int getI(PlayerClass pc, String path, int def) {
        String key = pc.name() + "::" + path;
        Integer cached = ints.get(key);
        if (cached != null) {
            return cached;
        }
        int v = readI(pc, path, def);
        ints.put(key, v);
        return v;
    }

    public boolean getB(PlayerClass pc, String path, boolean def) {
        String key = pc.name() + "::" + path;
        Boolean cached = bools.get(key);
        if (cached != null) {
            return cached;
        }
        boolean v = readB(pc, path, def);
        bools.put(key, v);
        return v;
    }

    public String getS(PlayerClass pc, String path, String def) {
        String key = pc.name() + "::" + path;
        String cached = strings.get(key);
        if (cached != null) {
            return cached;
        }
        String v = readS(pc, path, def);
        strings.put(key, v != null ? v : "");
        return v;
    }

    public ConfigurationSection section(PlayerClass pc, String subPath) {
        ConfigurationSection root = loader.sectionOf(pc);
        if (root == null) {
            return null;
        }
        return root.getConfigurationSection(subPath);
    }

    private double readD(PlayerClass pc, String path, double def) {
        ConfigurationSection s = loader.sectionOf(pc);
        if (s == null || !s.contains(path)) {
            return def;
        }
        double v = s.getDouble(path, Double.NaN);
        return Double.isFinite(v) ? v : def;
    }

    private int readI(PlayerClass pc, String path, int def) {
        ConfigurationSection s = loader.sectionOf(pc);
        if (s == null || !s.contains(path)) {
            return def;
        }
        return s.getInt(path, def);
    }

    private boolean readB(PlayerClass pc, String path, boolean def) {
        ConfigurationSection s = loader.sectionOf(pc);
        if (s == null || !s.contains(path)) {
            return def;
        }
        return s.getBoolean(path, def);
    }

    private String readS(PlayerClass pc, String path, String def) {
        ConfigurationSection s = loader.sectionOf(pc);
        if (s == null || !s.contains(path)) {
            return def;
        }
        return s.getString(path, def);
    }

    /** Размер кэша (для selftest/диагностики). */
    public int cacheSize() {
        return doubles.size() + ints.size() + bools.size() + strings.size();
    }
}

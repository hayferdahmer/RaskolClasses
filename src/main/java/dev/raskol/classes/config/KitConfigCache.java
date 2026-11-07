// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;

import dev.raskol.classes.classsystem.PlayerClass;

/**
 * 1.11.4 (P5): кэш значений, прочитанных через KitConfigLoader.
 * Сбрасывается полностью на каждом reload (/rc reload или стартап).
 *
 * Типовые вызовы:
 *   cache.getD(PlayerClass.WARLOCK, "recoil.percent", 6.66);
 *   cache.getI(PlayerClass.WARRIOR, "passives.execute_passive.cooldown-seconds", 6);
 *
 * Поддерживает dotted-пути через ConfigurationSection.get.
 *
 * 1.15.8 (Волна 8, аудит 2.8-16): дефолт ВХОДИТ в ключ кэша. Раньше ключ был
 *   pc::path, и первый вызов с дефолтом X «замораживал» X для всех последующих
 *   вызовов того же пути с другим дефолтом Y (возвращалось X). Теперь ключ
 *   pc::path::def — разные дефолты не пересекаются. Размер кэша растёт только
 *   на число различных дефолтов по пути (в практике — 1).
 */
public final class KitConfigCache {

    private final KitConfigLoader loader;
    private final Map<String, Double> doubles = new ConcurrentHashMap<>();
    private final Map<String, Integer> ints = new ConcurrentHashMap<>();
    private final Map<String, Boolean> bools = new ConcurrentHashMap<>();
    private final Map<String, String> strings = new ConcurrentHashMap<>();

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

    /** 1.15.8 (2.8-16): ключ включает дефолт — разные дефолты не пересекаются. */
    private static String key(PlayerClass pc, String path, Object def) {
        return pc.name() + "::" + path + "::" + def;
    }

    public double getD(PlayerClass pc, String path, double def) {
        String k = key(pc, path, def);
        Double cached = doubles.get(k);
        if (cached != null) {
            return cached;
        }
        double v = readD(pc, path, def);
        doubles.put(k, v);
        return v;
    }

    public int getI(PlayerClass pc, String path, int def) {
        String k = key(pc, path, def);
        Integer cached = ints.get(k);
        if (cached != null) {
            return cached;
        }
        int v = readI(pc, path, def);
        ints.put(k, v);
        return v;
    }

    public boolean getB(PlayerClass pc, String path, boolean def) {
        String k = key(pc, path, def);
        Boolean cached = bools.get(k);
        if (cached != null) {
            return cached;
        }
        boolean v = readB(pc, path, def);
        bools.put(k, v);
        return v;
    }

    public String getS(PlayerClass pc, String path, String def) {
        String k = key(pc, path, def);
        String cached = strings.get(k);
        if (cached != null) {
            return cached;
        }
        String v = readS(pc, path, def);
        strings.put(k, v != null ? v : "");
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

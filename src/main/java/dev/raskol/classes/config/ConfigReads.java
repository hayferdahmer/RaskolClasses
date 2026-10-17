// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 1.14.10 (Sprint 7, Волна 7.3, P0-1-01): ЕДИНАЯ точка чтения конфиг-значений
 * с защитой от некорректных данных. До этого идентичный хелпер cfgD()
 * (`double v = cfg.getDouble(path, def); return Double.isFinite(v) ? v : def;`)
 * был скопирован дословно в 9 классов (аудит 1-01): правка проверки (диапазон,
 * NaN) требовала 9 правок, поведение рисковало разойтись.
 *
 * Контракт:
 *  - finiteDouble: NaN/Infinity в конфиге → дефолт (не 0.0, не проброс NaN в формулы);
 *  - finiteInt: отсутствующий/некорректный ключ → дефолт;
 *  - stringOr: null/пусто-отсутствующий → дефолт (пустая строка из конфига сохраняется);
 *  - boolOr: отсутствующий ключ → дефолт;
 *  - null-секция безопасна во всех методах (возвращается дефолт).
 *
 * Pure-класс: работает с ConfigurationSection без Bukkit-сервера, покрыт
 * unit-тестом ConfigReadsTest (волна 7.3).
 */
public final class ConfigReads {

    private ConfigReads() {
    }

    /** Конечное double из конфига; NaN/Infinity/null-секция → def. */
    public static double finiteDouble(ConfigurationSection cfg, String path, double def) {
        if (cfg == null) {
            return def;
        }
        double v = cfg.getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    /** int из конфига; null-секция → def. */
    public static int finiteInt(ConfigurationSection cfg, String path, int def) {
        if (cfg == null) {
            return def;
        }
        return cfg.getInt(path, def);
    }

    /** Строка из конфига; отсутствующий ключ или null → def. */
    public static String stringOr(ConfigurationSection cfg, String path, String def) {
        if (cfg == null) {
            return def;
        }
        String v = cfg.getString(path, def);
        return v != null ? v : def;
    }

    /** boolean из конфига; отсутствующий ключ или null-секция → def. */
    public static boolean boolOr(ConfigurationSection cfg, String path, boolean def) {
        if (cfg == null) {
            return def;
        }
        return cfg.getBoolean(path, def);
    }
}

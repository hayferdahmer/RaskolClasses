// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.14.10 (Sprint 7, Волна 7.3, P0-1-01): unit-тесты ConfigReads.
 * YamlConfiguration инстанцируется headless (Bukkit-сервер не нужен).
 *
 * 1.14.10 (Sprint 7, Волна 7.3b): добавлен тест stringNonEmptyOr (пустая
 * строка из конфига → дефолт, для тегов/звуков AvoidanceService).
 */
@DisplayName("ConfigReads: NaN-guard и дефолты")
class ConfigReadsTest {

    private static final double EPS = 1e-9;

    private YamlConfiguration cfg;

    @BeforeEach
    void setUp() {
        cfg = new YamlConfiguration();
    }

    @Test
    @DisplayName("finiteDouble: сохранённое конечное значение читается")
    void finiteDoubleReadsStored() {
        cfg.set("a", 12.5);
        assertEquals(12.5, ConfigReads.finiteDouble(cfg, "a", 1.0), EPS);
    }

    @Test
    @DisplayName("finiteDouble: NaN в конфиге → дефолт (не проброс в формулы)")
    void finiteDoubleNaNfallsBack() {
        cfg.set("a", Double.NaN);
        assertEquals(1.0, ConfigReads.finiteDouble(cfg, "a", 1.0), EPS);
    }

    @Test
    @DisplayName("finiteDouble: Infinity в конфиге → дефолт")
    void finiteDoubleInfinityFallsBack() {
        cfg.set("a", Double.POSITIVE_INFINITY);
        assertEquals(1.0, ConfigReads.finiteDouble(cfg, "a", 1.0), EPS);
    }

    @Test
    @DisplayName("finiteDouble: отсутствующий ключ и null-секция → дефолт")
    void finiteDoubleMissingAndNull() {
        assertEquals(1.0, ConfigReads.finiteDouble(cfg, "missing", 1.0), EPS);
        assertEquals(1.0, ConfigReads.finiteDouble(null, "a", 1.0), EPS);
    }

    @Test
    @DisplayName("finiteInt: значение, отсутствующий ключ, null-секция")
    void finiteIntSemantics() {
        cfg.set("i", 42);
        assertEquals(42, ConfigReads.finiteInt(cfg, "i", 7));
        assertEquals(7, ConfigReads.finiteInt(cfg, "missing", 7));
        assertEquals(7, ConfigReads.finiteInt(null, "i", 7));
    }

    @Test
    @DisplayName("stringOr: значение, отсутствующий ключ, null-секция")
    void stringOrSemantics() {
        cfg.set("s", "x");
        assertEquals("x", ConfigReads.stringOr(cfg, "s", "def"));
        assertEquals("def", ConfigReads.stringOr(cfg, "missing", "def"));
        assertEquals("def", ConfigReads.stringOr(null, "s", "def"));
    }

    @Test
    @DisplayName("stringNonEmptyOr: null/пустая строка → def, непустая сохраняется")
    void stringNonEmptyOrSemantics() {
        cfg.set("tag", "💨 Уклонение!");
        assertEquals("💨 Уклонение!", ConfigReads.stringNonEmptyOr(cfg, "tag", "def"));
        cfg.set("empty", "");
        assertEquals("def", ConfigReads.stringNonEmptyOr(cfg, "empty", "def"));
        assertEquals("def", ConfigReads.stringNonEmptyOr(cfg, "missing", "def"));
        assertEquals("def", ConfigReads.stringNonEmptyOr(null, "tag", "def"));
    }

    @Test
    @DisplayName("boolOr: true/false из конфига, дефолт при отсутствии и null-секции")
    void boolOrSemantics() {
        cfg.set("b", true);
        assertTrue(ConfigReads.boolOr(cfg, "b", false));
        cfg.set("b2", false);
        assertFalse(ConfigReads.boolOr(cfg, "b2", true));
        assertTrue(ConfigReads.boolOr(cfg, "missing", true));
        assertFalse(ConfigReads.boolOr(null, "b", false));
    }
}

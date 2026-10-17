// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat.school;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.combat.DamageType;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * 1.12.0: конфиг-слой школ (секция schools.* в config.yml).
 *  - enabled: рубильник каркаса (false = всё работает как в 1.11.4);
 *  - multiplier.<SCHOOL>: глобальный нерф/бафф школы (0..10, дефолт 1.0);
 *  - vanilla-school.<CAUSE>: маппинг ванильных причин на школы;
 *    фолбэк — канал из damage-types.vanilla-map → дефолтная школа канала;
 *  - mitigation-cap / pen-pct-cap / elemental.*: резерв 1.12.1–1.12.2.
 * 1.12.0-reorg: пакет combat.school.
 * 1.14.12 (Sprint 8, Волна 12C, P0-6-06): logger.warning с ключом конфига
 *         вместо пустого catch (IllegalArgumentException ignored) при парсинге
 *         DamageType из damage-types.vanilla-map — битое значение логируется
 *         с указанием ключа, а не проглатывается молча.
 */
public final class SchoolConfig {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");
    private final RaskolClasses plugin;

    public SchoolConfig(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("schools.enabled", true);
    }

    /** Глобальный множитель школы (кламп 0..10; битое значение → 1.0). */
    public double multiplier(School school) {
        double v = plugin.getConfig().getDouble(
                "schools.multiplier." + school.id(), 1.0);
        if (!Double.isFinite(v) || v < 0.0 || v > 10.0) {
            return 1.0;
        }
        return v;
    }

    /** Резерв 1.12.1: кап суммарного поглощения (митигации). */
    public double mitigationCap() {
        double v = plugin.getConfig().getDouble("schools.mitigation-cap", 0.80);
        return Double.isFinite(v) && v >= 0.0 && v <= 1.0 ? v : 0.80;
    }

    /** Резерв 1.12.1: кап процентного пробития. */
    public double penPctCap() {
        double v = plugin.getConfig().getDouble("schools.pen-pct-cap", 0.40);
        return Double.isFinite(v) && v >= 0.0 && v <= 1.0 ? v : 0.40;
    }

    /** Резерв 1.12.2: стихийный слой резистов поверх маг-резиста. */
    public boolean elementalEnabled() {
        return plugin.getConfig().getBoolean("schools.elemental.enabled", false);
    }

    public double elementalResistCap() {
        double v = plugin.getConfig().getDouble("schools.elemental.resist-cap", 60.0);
        return Double.isFinite(v) && v >= 0.0 && v <= 100.0 ? v : 60.0;
    }

    /**
     * Школа ванильной причины урона: schools.vanilla-school.<CAUSE> →
     * фолбэк через канал (damage-types.vanilla-map → DamageType.defaultFor).
     * 1.14.12 (Волна 12C, 6-06): битое значение в damage-types.vanilla-map
     * логируется с ключом конфига (раньше проглатывалось молча).
     */
    public School schoolOf(EntityDamageEvent.DamageCause cause) {
        String override = plugin.getConfig()
                .getString("schools.vanilla-school." + cause.name());
        School direct = School.fromId(override);
        if (direct != null) {
            return direct;
        }
        String channelKey = "damage-types.vanilla-map." + cause.name();
        String channelOverride = plugin.getConfig().getString(channelKey);
        DamageType channel = null;
        if (channelOverride != null && !channelOverride.isEmpty()) {
            try {
                channel = DamageType.valueOf(channelOverride.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                LOGGER.warning("SchoolConfig: битое значение ключа '" + channelKey
                        + "' = '" + channelOverride + "' — используется дефолтная школа канала");
            }
        }
        if (channel == null) {
            channel = DamageType.defaultFor(cause);
        }
        return defaultSchoolForChannel(channel);
    }

    /** Дефолтная школа канала: PHYSICAL→PHYSICAL, MAGIC→ARCANE, TRUE→TRUE. */
    public static School defaultSchoolForChannel(DamageType channel) {
        return switch (channel) {
            case PHYSICAL -> School.PHYSICAL;
            case MAGIC -> School.ARCANE;
            case TRUE -> School.TRUE;
        };
    }
}

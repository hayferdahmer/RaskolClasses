// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.ResistService;

/**
 * 1.9.1: харденинг конфига (расширение с 1.6.7).
 * 1.10.1: logSummary печатает ТОЛЬКО пять основных классов —
 *         Чернокнижник скрыт из стартовых логов.
 *
 * 1.15.8 (Волна 8, аудит 2.3-1): проверки legacy-ключей talents.* УДАЛЕНЫ вместе
 *         с самими ключами из config.yml (legacy-слой talent/spec мёртв с 1.14.0).
 * 1.15.8 (Волна 8, аудит 2.8-13): сообщение warning больше не обещает «применено
 *         значение по умолчанию» от валидатора — формулировка честная: значение
 *         НЕВАЛИДНО, читатель (ConfigReads/геттеры с гардами) применит дефолт/зажим.
 *         Покрытие расширено: spec2 (start-level/max-points/row-gates/respec),
 *         avoidance (k-коэффициенты/капы), cc (окно/капы/порог), combat (burst/капы),
 *         item (daily-limit ≥ 1), plus (цена/периоды) — секции, которые валидатор
 *         раньше не смотрел вовсе.
 */
public final class ConfigValidator {

    private static final Set<String> DAMAGE_TYPES = Set.of("physical", "magic", "true");

    private final RaskolClasses plugin;
    private int problems;

    public ConfigValidator(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public int validate() {
        problems = 0;
        FileConfiguration cfg = plugin.getConfig();

        checkRange(cfg, "resist.cap", 0.0, 100.0);

        ConfigurationSection classes = cfg.getConfigurationSection("resist.classes");
        if (classes != null) {
            for (String pc : classes.getKeys(false)) {
                checkRange(cfg, "resist.classes." + pc + ".physical", 0.0, 100.0);
                checkRange(cfg, "resist.classes." + pc + ".magic", 0.0, 100.0);
            }
        }

        ConfigurationSection grants = cfg.getConfigurationSection("resist.grants");
        if (grants != null) {
            for (String id : grants.getKeys(false)) {
                checkRange(cfg, "resist.grants." + id + ".physical", 0.0, 100.0);
                checkRange(cfg, "resist.grants." + id + ".magic", 0.0, 100.0);
            }
        }

        checkRange(cfg, "resist.mana-soaked.physical", 0.0, 100.0);
        checkRange(cfg, "resist.mana-soaked.magic", 0.0, 100.0);

        ConfigurationSection specs = cfg.getConfigurationSection("resist.specs");
        if (specs != null) {
            for (String id : specs.getKeys(false)) {
                checkRange(cfg, "resist.specs." + id + ".physical", 0.0, 100.0);
                checkRange(cfg, "resist.specs." + id + ".magic", 0.0, 100.0);
            }
        }

        ConfigurationSection map = cfg.getConfigurationSection("damage-types.vanilla-map");
        if (map != null) {
            for (String cause : map.getKeys(false)) {
                String value = map.getString(cause, "");
                if (value == null || !DAMAGE_TYPES.contains(value.toLowerCase(Locale.ROOT))) {
                    warn("damage-types.vanilla-map." + cause, String.valueOf(value), "physical|magic|true");
                }
            }
        }

        ConfigurationSection cls = cfg.getConfigurationSection("classes");
        if (cls != null) {
            for (String pc : cls.getKeys(false)) {
                ConfigurationSection abilities = cls.getConfigurationSection(pc + ".abilities");
                if (abilities == null) {
                    continue;
                }
                for (String id : abilities.getKeys(false)) {
                    checkNonNegative(cfg, "classes." + pc + ".abilities." + id + ".damage-physical");
                    checkNonNegative(cfg, "classes." + pc + ".abilities." + id + ".damage-magic");
                }
            }
        }

        ConfigurationSection installs = cfg.getConfigurationSection("installations");
        if (installs != null) {
            for (String id : installs.getKeys(false)) {
                checkNonNegative(cfg, "installations." + id + ".damage-physical");
                checkNonNegative(cfg, "installations." + id + ".damage-magic");
            }
        }

        // 1.15.8 (2.3-1): legacy-проверки talents.* удалены вместе с ключами.

        checkPositive(cfg, "character-level.top-n", 1);
        checkNonNegative(cfg, "character-level.fallback");

        // 1.15.8 (2.8-13): combat — burst-окно и капы
        checkPositive(cfg, "combat.burst-window-seconds", 1);
        checkRange(cfg, "combat.burst-window-pct", 0.0, 100.0);
        checkRange(cfg, "combat.max-single-hit-pct", 0.0, 100.0);
        checkNonNegative(cfg, "combat.dot-dps-cap-pct");
        checkNonNegative(cfg, "combat.true-damage-cap-per-hit");

        // 1.15.8 (2.8-13): spec2 — очки, гейты рядов, респец
        checkPositive(cfg, "spec2.start-level", 1);
        checkPositive(cfg, "spec2.max-points", 1);
        checkPositive(cfg, "spec2.tree-capacity-min", 1);
        checkNonNegative(cfg, "spec2.respec-base-cost");
        checkNonNegative(cfg, "spec2.respec-per-level");
        checkNonNegative(cfg, "spec2.node-respec-base");
        checkNonNegative(cfg, "spec2.node-respec-per-rank");
        checkRowGates(cfg);

        // 1.15.8 (2.8-13): avoidance — коэффициенты и капы уклонения/парирования
        checkPositive(cfg, "avoidance.dodge-k", 1);
        checkPositive(cfg, "avoidance.parry-k", 1);
        checkRange(cfg, "avoidance.soft-cap", 0.0, 100.0);
        checkRange(cfg, "avoidance.hard-cap", 0.0, 100.0);
        checkRange(cfg, "avoidance.dr-factor", 0.0, 1.0);
        checkRange(cfg, "avoidance.dodge-mult", 0.0, 1.0);
        checkRange(cfg, "avoidance.front-angle", 0.0, 360.0);
        checkRange(cfg, "avoidance.back-angle", 0.0, 360.0);

        // 1.15.8 (2.8-13): cc — окно DR и капы сопротивлений
        checkPositive(cfg, "cc.window-seconds", 1);
        checkRange(cfg, "cc.resist-cap", 0.0, 1.0);
        checkRange(cfg, "cc.power-cap", 0.0, 1.0);
        checkRange(cfg, "cc.duration-reduction-cap", 0.0, 1.0);
        checkRange(cfg, "cc.breaks-on-damage-threshold-pct", 0.0, 1.0);
        checkDrMultipliers(cfg);

        // 1.15.8 (2.8-13): item — дневной лимит разбора ≥ 1 (гард в SalvageService)
        checkPositive(cfg, "item.salvage.daily-limit", 1);

        // 1.15.8 (2.8-13): plus — экономика подписки
        checkPositive(cfg, "plus.price-per-period", 1);
        checkPositive(cfg, "plus.period-days", 1);
        checkPositive(cfg, "plus.max-gift-periods", 1);
        checkNonNegative(cfg, "plus.cooldown-gift-ms");

        checkNonNegative(cfg, "installations.frost_rune.radius");
        checkNonNegative(cfg, "installations.frost_rune.duration");
        checkNonNegative(cfg, "installations.frost_rune.cooldown");
        checkNonNegative(cfg, "installations.frost_rune.damage-base");
        checkNonNegative(cfg, "installations.frost_rune.damage-ramp");
        checkNonNegative(cfg, "installations.frost_rune.damage-cap");
        checkPositive(cfg, "installations.frost_rune.slow-ramp-every", 1);
        checkNonNegative(cfg, "installations.frost_rune.slow-max-tier");
        checkNonNegative(cfg, "installations.frost_rune.mage-mana-per-sec");
        checkNonNegative(cfg, "installations.frost_rune.mage-int-mult");

        checkNonNegative(cfg, "attributes.hp.base-hp");
        checkNonNegative(cfg, "attributes.hp.per-str");
        checkNonNegative(cfg, "attributes.hp.per-level");
        checkNonNegative(cfg, "attributes.hp.main-str-bonus");
        checkNonNegative(cfg, "attributes.hp.regen-per-str");
        checkNonNegative(cfg, "attributes.hp.regen-combat-factor");
        checkNonNegative(cfg, "attributes.hp.regen-cap-pct");

        // 1.10.1: sanity-ключи фолианта (дроп в аду)
        checkRange(cfg, "foliant.drop-chance-percent", 0.0, 100.0);

        if (problems == 0) {
            plugin.getLogger()
                    .info("ConfigValidator: конфиг валиден "
                            + "(resist/damage/classes/installations/character-level/combat/spec2/"
                            + "avoidance/cc/item/plus/attributes/foliant).");
        } else {
            plugin.getLogger()
                    .warning("ConfigValidator: невалидных ключей: " + problems
                            + " (см. WARNING выше; читатели применят дефолт/зажим).");
        }
        return problems;
    }

    /**
     * 1.15.8 (2.8-13): row-gates — список целых ≥ 0, неубывающий, длина ≥ 2.
     * Нарушение = warning: Spec2Points.snapshot() использует глобальные гейты,
     * пер-tree override'ы проверяются отдельно в selftest-чеке 153.
     */
    private void checkRowGates(FileConfiguration cfg) {
        List<?> gates = cfg.getList("spec2.row-gates", null);
        if (gates == null) {
            return;
        }
        if (gates.size() < 2) {
            warn("spec2.row-gates", "size=" + gates.size(), "≥ 2 значений");
            return;
        }
        int prev = -1;
        for (int i = 0; i < gates.size(); i++) {
            Object v = gates.get(i);
            if (!(v instanceof Number n) || n.intValue() < 0) {
                warn("spec2.row-gates[" + i + "]", String.valueOf(v), "целое ≥ 0");
                return;
            }
            int cur = n.intValue();
            if (cur < prev) {
                warn("spec2.row-gates[" + i + "]", String.valueOf(cur), "не убывание (предыдущее " + prev + ")");
                return;
            }
            prev = cur;
        }
    }

    /**
     * 1.15.8 (2.8-13): cc.dr-multipliers — непустой список дробей [0..1],
     * последний элемент = 0.0 (иммунитет после исчерпания стеков).
     */
    private void checkDrMultipliers(FileConfiguration cfg) {
        List<?> mults = cfg.getDoubleList("cc.dr-multipliers");
        if (mults == null || mults.isEmpty()) {
            return;
        }
        for (int i = 0; i < mults.size(); i++) {
            Object v = mults.get(i);
            if (!(v instanceof Number n)
                    || !Double.isFinite(n.doubleValue())
                    || n.doubleValue() < 0.0
                    || n.doubleValue() > 1.0) {
                warn("cc.dr-multipliers[" + i + "]", String.valueOf(v), "дробь [0..1]");
            }
        }
    }

    /** 1.10.1: сводка только по пяти основным классам (Чернокнижник скрыт). */
    public void logSummary() {
        StringBuilder sb = new StringBuilder("Базы резистов (маг/физ): ");
        for (PlayerClass pc : PlayerClass.values()) {
            if (pc == PlayerClass.WARLOCK) {
                continue;
            }
            double baseMagic = 0;
            double basePhys = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (plugin.getClassProvider().getClassOf(online) == pc) {
                    ResistService.Breakdown rb = plugin.getResists().breakdown(online.getUniqueId());
                    baseMagic = rb.baseMagic();
                    basePhys = rb.basePhysical();
                    break;
                }
            }
            sb.append(pc.name())
                    .append(' ')
                    .append((int) baseMagic)
                    .append('/')
                    .append((int) basePhys)
                    .append("; ");
        }
        sb.append("кап ").append((int) plugin.getResists().cap());
        plugin.getLogger().info(sb.toString());
    }

    private void checkRange(FileConfiguration cfg, String path, double min, double max) {
        if (!cfg.isSet(path)) {
            return;
        }
        double v = cfg.getDouble(path, min);
        if (!Double.isFinite(v) || v < min || v > max) {
            warn(path, String.valueOf(v), min + ".." + max);
        }
    }

    private void checkNonNegative(FileConfiguration cfg, String path) {
        if (!cfg.isSet(path)) {
            return;
        }
        double v = cfg.getDouble(path, 0.0);
        if (!Double.isFinite(v) || v < 0.0) {
            warn(path, String.valueOf(v), ">= 0");
        }
    }

    private void checkPositive(FileConfiguration cfg, String path, int min) {
        if (!cfg.isSet(path)) {
            return;
        }
        int v = cfg.getInt(path, min);
        if (v < min) {
            warn(path, String.valueOf(v), ">= " + min);
        }
    }

    /**
     * 1.15.8 (2.8-13): честная формулировка — валидатор НЕ подменяет значение,
     * он только сигнализирует; дефолт/зажим применяет читатель (ConfigReads
     * или геттер с гардом). Прежний текст вводил в заблуждение.
     */
    private void warn(String path, String value, String expected) {
        problems++;
        plugin.getLogger()
                .warning("RaskolClasses конфиг: некорректно " + path + " = " + value
                        + " (ожидание: " + expected + ") — читатель применит дефолт/зажим, "
                        + "значение в конфиге игнорируется.");
    }
}

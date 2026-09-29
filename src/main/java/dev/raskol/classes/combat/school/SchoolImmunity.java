// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat.school;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * 1.12.1: иммунитеты/устойчивости/уязвимости сущностей к школам (ТЗ п.8).
 * Конфиг: schools.entities.<ENTITY_TYPE>.immune / resistant / vulnerable (списки школ)
 * + resistant-mult (дефолт 0.5) / vulnerable-mult (дефолт 1.5).
 * Порядок: immune (урон 0, триггеры молчат) → vulnerable → resistant → 1.0.
 * Пустая таблица = множитель 1.0 везде (поведение 1.11.4).
 */
public final class SchoolImmunity {

    private final RaskolClasses plugin;

    public SchoolImmunity(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public double multiplierFor(EntityType type, School school) {
        if (type == null || school == null) {
            return 1.0;
        }
        String base = "schools.entities." + type.name() + ".";
        if (inList(base + "immune", school)) {
            return 0.0;
        }
        if (inList(base + "vulnerable", school)) {
            return cfgD(base + "vulnerable-mult", 1.5);
        }
        if (inList(base + "resistant", school)) {
            return cfgD(base + "resistant-mult", 0.5);
        }
        return 1.0;
    }

    /** true = школа полностью игнорируется сущностью (урон 0, DoT-триггеры не срабатывают). */
    public boolean isImmune(EntityType type, School school) {
        return multiplierFor(type, school) == 0.0;
    }

    private boolean inList(String path, School school) {
        List<String> list = plugin.getConfig().getStringList(path);
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (String s : list) {
            School parsed = School.fromId(s);
            if (parsed == school) {
                return true;
            }
        }
        return false;
    }

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }
}

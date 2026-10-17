// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.attribute;

import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

/**
 * 1.14.12 (Sprint 8, Волна 12A, P0-3-02): единая точка чтения максимума здоровья
 * через атрибут MAX_HEALTH вместо deprecated LivingEntity#getMaxHealth()
 * (аудит 3-02: 4 вызова в DamageCaps/CombatService/ResourceService/WarlockAbilities).
 *
 * Контракт:
 *  - null-сущность и отсутствующий атрибут → 20.0 (ванильный дефолт, безопасно для формул);
 *  - не-конечное или ≤ 0 значение атрибута → 20.0;
 *  - атрибут берётся из AttributeService.maxHealthAttr() — один registry-lookup
 *    на жизненный цикл плагина, без дублирования статик-инициализации.
 */
public final class Hp {

    private Hp() {
    }

    /** Максимум здоровья сущности в carrier-единицах (ванильные сердца). */
    public static double max(LivingEntity entity) {
        if (entity == null) {
            return 20.0;
        }
        AttributeInstance instance = entity.getAttribute(AttributeService.maxHealthAttr());
        if (instance == null) {
            return 20.0;
        }
        double v = instance.getValue();
        return Double.isFinite(v) && v > 0.0 ? v : 20.0;
    }
}

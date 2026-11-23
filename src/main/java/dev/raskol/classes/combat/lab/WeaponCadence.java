// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.lab;

import java.util.Map;

import dev.raskol.classes.item.model.WeaponType;

/** Чистая модель времени атаки; не зависит от Bukkit и может работать в headless CI. */
public final class WeaponCadence {

    private static final Map<String, Double> PAPER_26_2_MATERIAL_ATTACK_SPEED = Map.of(
            "IRON_SWORD", 1.6,
            "IRON_AXE", 0.9,
            "MACE", 0.6,
            "TRIDENT", 1.1,
            "BLAZE_ROD", 4.0);

    public record Profile(double intervalSeconds, String modelId) {
        public Profile {
            if (!Double.isFinite(intervalSeconds) || intervalSeconds <= 0.0 || modelId == null || modelId.isBlank()) {
                throw new IllegalArgumentException("Некорректный профиль темпа атаки");
            }
        }
    }

    private WeaponCadence() {}

    public static Profile unarmed() {
        return new Profile(0.25, "PLAYER_UNARMED_DEFAULT_4_ATTACKS_PER_SECOND");
    }

    public static Profile forWeapon(WeaponType weaponType, String materialId, double attackSpeedBonusPct) {
        if (weaponType == null
                || materialId == null
                || materialId.isBlank()
                || !Double.isFinite(attackSpeedBonusPct)
                || attackSpeedBonusPct < 0.0) {
            throw new IllegalArgumentException("Тип оружия, материал и бонус скорости обязательны");
        }
        if (weaponType == WeaponType.BOW) {
            return new Profile(1.0, "BOW_FULL_CHARGE_20_TICKS");
        }
        if (weaponType == WeaponType.CROSSBOW) {
            return new Profile(1.25, "CROSSBOW_RELOAD_25_TICKS");
        }
        double baseAttackSpeed =
                PAPER_26_2_MATERIAL_ATTACK_SPEED.getOrDefault(materialId.toUpperCase(java.util.Locale.ROOT), 0.0);
        if (baseAttackSpeed <= 0.0) {
            throw new IllegalArgumentException(
                    "Нет проверенного Paper 26.2 профиля скорости для материала: " + materialId);
        }
        double effectiveAttackSpeed = baseAttackSpeed * (1.0 + attackSpeedBonusPct / 100.0);
        return new Profile(1.0 / effectiveAttackSpeed, "PAPER_26_2_MATERIAL_ATTACK_SPEED");
    }

    public static java.util.Set<String> referenceMaterials() {
        return PAPER_26_2_MATERIAL_ATTACK_SPEED.keySet();
    }

    public static double referenceAttackSpeed(String materialId) {
        if (materialId == null) return 0.0;
        return PAPER_26_2_MATERIAL_ATTACK_SPEED.getOrDefault(materialId.toUpperCase(java.util.Locale.ROOT), 0.0);
    }
}

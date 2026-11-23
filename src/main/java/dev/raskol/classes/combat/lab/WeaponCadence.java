// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.lab;

import java.util.Collection;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;

import dev.raskol.classes.item.model.WeaponType;

/** Расчёт интервала автоатаки по атрибутам и таймингам Paper/Minecraft runtime. */
public final class WeaponCadence {

    public record Profile(double intervalSeconds, String modelId) {
        public Profile {
            if (!Double.isFinite(intervalSeconds) || intervalSeconds <= 0.0 || modelId == null || modelId.isBlank()) {
                throw new IllegalArgumentException("Некорректный профиль темпа атаки");
            }
        }
    }

    private WeaponCadence() {}

    public static Profile unarmed() {
        return new Profile(1.0 / playerBaseAttackSpeed(), "PLAYER_UNARMED_DEFAULT");
    }

    public static Profile forWeapon(WeaponType weaponType, String materialId, double attackSpeedBonusPct) {
        if (weaponType == null || materialId == null || materialId.isBlank()
                || !Double.isFinite(attackSpeedBonusPct) || attackSpeedBonusPct < 0.0) {
            throw new IllegalArgumentException("Тип оружия, материал и бонус скорости обязательны");
        }
        if (weaponType == WeaponType.BOW) {
            return new Profile(1.0, "BOW_FULL_CHARGE_20_TICKS");
        }
        if (weaponType == WeaponType.CROSSBOW) {
            return new Profile(1.25, "CROSSBOW_RELOAD_25_TICKS");
        }

        Material material = Material.matchMaterial(materialId);
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("Неизвестный Paper Material для оружия: " + materialId);
        }
        Collection<AttributeModifier> modifiers =
                material.getDefaultAttributeModifiers(EquipmentSlot.HAND).get(Attribute.ATTACK_SPEED);
        double additive = 0.0;
        double multipliedBase = 0.0;
        double multipliedTotal = attackSpeedBonusPct / 100.0;
        for (AttributeModifier modifier : modifiers) {
            switch (modifier.getOperation()) {
                case ADD_NUMBER -> additive += modifier.getAmount();
                case ADD_SCALAR -> multipliedBase += modifier.getAmount();
                case MULTIPLY_SCALAR_1 -> multipliedTotal += modifier.getAmount();
            }
        }
        double base = playerBaseAttackSpeed();
        double effective = (base + additive + base * multipliedBase) * (1.0 + multipliedTotal);
        if (!Double.isFinite(effective) || effective <= 0.0) {
            throw new IllegalArgumentException("Paper Material даёт некорректную скорость атаки: " + materialId);
        }
        return new Profile(1.0 / effective, "PLAYER_MATERIAL_ATTRIBUTE");
    }

    private static double playerBaseAttackSpeed() {
        AttributeInstance attribute = EntityType.PLAYER.getDefaultAttributes().getAttribute(Attribute.ATTACK_SPEED);
        if (attribute == null || !Double.isFinite(attribute.getBaseValue()) || attribute.getBaseValue() <= 0.0) {
            throw new IllegalStateException("Paper не предоставил базовый ATTACK_SPEED для PLAYER");
        }
        return attribute.getBaseValue();
    }
}

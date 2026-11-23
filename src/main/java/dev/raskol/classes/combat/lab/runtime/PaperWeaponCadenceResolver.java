// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.lab.runtime;

import java.util.Collection;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;

import dev.raskol.classes.combat.lab.WeaponCadence;

/** Paper runtime-аудит значений скорости оружейных материалов против headless-профиля. */
public final class PaperWeaponCadenceResolver {

    public record AuditResult(String material, double expectedAttackSpeed, double paperAttackSpeed) {
        public boolean matches() {
            return Math.abs(expectedAttackSpeed - paperAttackSpeed) <= 1.0e-9;
        }
    }

    private PaperWeaponCadenceResolver() {}

    public static java.util.List<AuditResult> auditConfiguredMaterials() {
        return WeaponCadence.referenceMaterials().stream()
                .sorted()
                .map(PaperWeaponCadenceResolver::auditMaterial)
                .toList();
    }

    public static AuditResult auditMaterial(String materialId) {
        Material material = Material.matchMaterial(materialId);
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("Неизвестный Paper Material: " + materialId);
        }
        AttributeInstance playerAttackSpeed =
                EntityType.PLAYER.getDefaultAttributes().getAttribute(Attribute.ATTACK_SPEED);
        if (playerAttackSpeed == null) {
            throw new IllegalStateException("Paper не предоставил PLAYER ATTACK_SPEED");
        }

        double base = playerAttackSpeed.getBaseValue();
        Collection<AttributeModifier> modifiers =
                material.getDefaultAttributeModifiers(EquipmentSlot.HAND).get(Attribute.ATTACK_SPEED);
        double additive = 0.0;
        double multipliedBase = 0.0;
        double multipliedTotal = 0.0;
        for (AttributeModifier modifier : modifiers) {
            switch (modifier.getOperation()) {
                case ADD_NUMBER -> additive += modifier.getAmount();
                case ADD_SCALAR -> multipliedBase += modifier.getAmount();
                case MULTIPLY_SCALAR_1 -> multipliedTotal += modifier.getAmount();
            }
        }
        double paperValue = (base + additive + base * multipliedBase) * (1.0 + multipliedTotal);
        return new AuditResult(material.name(), WeaponCadence.referenceAttackSpeed(material.name()), paperValue);
    }
}

// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

/**
 * Неизменяемая атрибуция одного боевого события.
 *
 * Контекст описывает источник и владельца отдельно: снаряд или питомец не
 * должны ошибочно считаться самостоятельным игроком. Неизвестные данные
 * остаются UNKNOWN/null — класс не пытается угадывать их по имени сущности.
 */
public record DamageSourceContext(
        UUID sourceEntityId,
        UUID attackerId,
        UUID ownerId,
        UUID targetId,
        SourceType sourceType,
        String abilityId,
        WeaponHand weaponHand,
        boolean fromProc,
        boolean reflected) {

    public enum SourceType {
        PLAYER,
        PROJECTILE,
        PET,
        TRAP,
        ABILITY,
        ENTITY,
        ENVIRONMENT,
        REFLECTION
    }

    public enum WeaponHand {
        MAIN_HAND,
        OFF_HAND,
        UNKNOWN
    }

    public DamageSourceContext {
        sourceType = sourceType == null ? SourceType.ENVIRONMENT : sourceType;
        weaponHand = weaponHand == null ? WeaponHand.UNKNOWN : weaponHand;
        abilityId = normalizeAbilityId(abilityId);
        if (sourceType == SourceType.ENVIRONMENT) {
            sourceEntityId = null;
            attackerId = null;
            ownerId = null;
            weaponHand = WeaponHand.UNKNOWN;
        }
        if (sourceType == SourceType.PLAYER && ownerId == null) {
            ownerId = attackerId;
        }
        if (sourceType == SourceType.REFLECTION) {
            reflected = true;
        }
    }

    public static DamageSourceContext infer(Entity source, Entity target, boolean fromProc, boolean reflected) {
        UUID targetId = target == null ? null : target.getUniqueId();
        if (source == null) {
            return new DamageSourceContext(
                    null, null, null, targetId, SourceType.ENVIRONMENT, null, WeaponHand.UNKNOWN, fromProc, reflected);
        }
        if (source instanceof Player player) {
            return new DamageSourceContext(
                    source.getUniqueId(),
                    player.getUniqueId(),
                    player.getUniqueId(),
                    targetId,
                    reflected ? SourceType.REFLECTION : SourceType.PLAYER,
                    null,
                    WeaponHand.UNKNOWN,
                    fromProc,
                    reflected);
        }
        if (source instanceof Projectile projectile) {
            UUID shooterId = projectile.getShooter() instanceof Entity shooter ? shooter.getUniqueId() : null;
            return new DamageSourceContext(
                    source.getUniqueId(),
                    shooterId,
                    shooterId,
                    targetId,
                    reflected ? SourceType.REFLECTION : SourceType.PROJECTILE,
                    null,
                    WeaponHand.UNKNOWN,
                    fromProc,
                    reflected);
        }
        return new DamageSourceContext(
                source.getUniqueId(),
                source.getUniqueId(),
                null,
                targetId,
                reflected ? SourceType.REFLECTION : SourceType.ENTITY,
                null,
                WeaponHand.UNKNOWN,
                fromProc,
                reflected);
    }

    public static DamageSourceContext pet(UUID petId, UUID ownerId, UUID targetId, String abilityId, boolean fromProc) {
        return new DamageSourceContext(
                petId, ownerId, ownerId, targetId, SourceType.PET, abilityId, WeaponHand.UNKNOWN, fromProc, false);
    }

    public static DamageSourceContext trap(
            UUID trapId, UUID ownerId, UUID targetId, String abilityId, boolean fromProc) {
        return new DamageSourceContext(
                trapId, ownerId, ownerId, targetId, SourceType.TRAP, abilityId, WeaponHand.UNKNOWN, fromProc, false);
    }

    public static DamageSourceContext ability(UUID casterId, UUID targetId, String abilityId, boolean fromProc) {
        return new DamageSourceContext(
                casterId,
                casterId,
                casterId,
                targetId,
                SourceType.ABILITY,
                abilityId,
                WeaponHand.UNKNOWN,
                fromProc,
                false);
    }

    public DamageSourceContext withWeaponHand(WeaponHand hand) {
        return new DamageSourceContext(
                sourceEntityId, attackerId, ownerId, targetId, sourceType, abilityId, hand, fromProc, reflected);
    }

    private static String normalizeAbilityId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9_:-]{1,64}") ? normalized : null;
    }
}

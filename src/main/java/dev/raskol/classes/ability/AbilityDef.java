// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

/**
 * Immutable-описание способности. Числа приходят из config.yml,
 * слот = порядковый номер (1-based). Длительности эффектов (D1/D2)
 * читаются способностями напрямую из конфига, а не хранятся здесь.
 */
public record AbilityDef(
        String id,
        String displayName,
        int slot,
        int unlockLevel,
        int cost,
        long cooldownMillis
) {
}

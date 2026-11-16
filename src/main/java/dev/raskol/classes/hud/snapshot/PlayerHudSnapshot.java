// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.snapshot;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import dev.raskol.classes.targeting.domain.TargetReadResult;

/** Полностью неизменяемый read snapshot одного игрока для всех HUD-виджетов. */
public record PlayerHudSnapshot(
        UUID playerId,
        long revision,
        long capturedAtMillis,
        CharacterView character,
        HealthView health,
        ResourceView resource,
        AttributeView attributes,
        TargetReadResult target,
        List<EffectView> effects,
        List<DotView> dots,
        CombatView combat,
        HudProfileView profile) {

    public PlayerHudSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(character, "character");
        Objects.requireNonNull(health, "health");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(dots, "dots");
        Objects.requireNonNull(combat, "combat");
        Objects.requireNonNull(profile, "profile");
        if (revision <= 0) {
            throw new IllegalArgumentException("revision должна быть положительной");
        }
        if (capturedAtMillis < 0) {
            throw new IllegalArgumentException("capturedAtMillis не может быть отрицательным");
        }
        effects = List.copyOf(effects);
        dots = List.copyOf(dots);
        if (effects.stream().anyMatch(Objects::isNull) || dots.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Коллекции snapshot не должны содержать null");
        }
    }

    public record CharacterView(
            HudValue<String> classId,
            HudValue<String> classDisplayName,
            HudValue<String> specializationId,
            HudValue<String> specializationRole,
            HudValue<Integer> characterLevel,
            HudValue<Long> currentExperience,
            HudValue<Long> experienceToNextLevel) {
        public CharacterView {
            Objects.requireNonNull(classId, "classId");
            Objects.requireNonNull(classDisplayName, "classDisplayName");
            Objects.requireNonNull(specializationId, "specializationId");
            Objects.requireNonNull(specializationRole, "specializationRole");
            Objects.requireNonNull(characterLevel, "characterLevel");
            Objects.requireNonNull(currentExperience, "currentExperience");
            Objects.requireNonNull(experienceToNextLevel, "experienceToNextLevel");
            validateNonNegative(characterLevel, "characterLevel");
            validateNonNegative(currentExperience, "currentExperience");
            validateNonNegative(experienceToNextLevel, "experienceToNextLevel");
        }
    }

    public record HealthView(HudValue<Double> currentFormulaHp, HudValue<Double> maxFormulaHp) {
        public HealthView {
            Objects.requireNonNull(currentFormulaHp, "currentFormulaHp");
            Objects.requireNonNull(maxFormulaHp, "maxFormulaHp");
            validateNonNegative(currentFormulaHp, "currentFormulaHp");
            validateNonNegative(maxFormulaHp, "maxFormulaHp");
            if (currentFormulaHp.status() != maxFormulaHp.status()) {
                throw new IllegalArgumentException("Статусы current/max HP должны совпадать");
            }
            if (maxFormulaHp.status() == DataStatus.AVAILABLE && maxFormulaHp.value() <= 0.0d) {
                throw new IllegalArgumentException("maxFormulaHp должен быть положительным");
            }
        }

        public OptionalDouble normalizedRatio() {
            if (currentFormulaHp.status() != DataStatus.AVAILABLE) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(Math.max(0.0d, Math.min(1.0d, currentFormulaHp.value() / maxFormulaHp.value())));
        }
    }

    public record ResourceView(
            HudValue<String> resourceId,
            HudValue<String> displayName,
            HudValue<String> symbol,
            HudValue<Double> current,
            HudValue<Double> effectiveMax) {
        public ResourceView {
            Objects.requireNonNull(resourceId, "resourceId");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(current, "current");
            Objects.requireNonNull(effectiveMax, "effectiveMax");
            validateNonNegative(current, "current resource");
            validateNonNegative(effectiveMax, "effectiveMax");
            if (current.status() != effectiveMax.status()) {
                throw new IllegalArgumentException("Статусы current/max ресурса должны совпадать");
            }
            if (effectiveMax.status() == DataStatus.AVAILABLE && effectiveMax.value() <= 0.0d) {
                throw new IllegalArgumentException("effectiveMax должен быть положительным");
            }
        }

        public OptionalDouble normalizedRatio() {
            if (current.status() != DataStatus.AVAILABLE) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(Math.max(0.0d, Math.min(1.0d, current.value() / effectiveMax.value())));
        }
    }

    public record AttributeView(HudValue<Double> strength, HudValue<Double> agility, HudValue<Double> intellect) {
        public AttributeView {
            Objects.requireNonNull(strength, "strength");
            Objects.requireNonNull(agility, "agility");
            Objects.requireNonNull(intellect, "intellect");
            validateNonNegative(strength, "strength");
            validateNonNegative(agility, "agility");
            validateNonNegative(intellect, "intellect");
        }
    }

    public record EffectView(
            String id,
            String displayName,
            Optional<String> iconKey,
            long expiresAtMillis,
            boolean indefinite,
            int stacks) {
        public EffectView {
            requireText(id, "id");
            requireText(displayName, "displayName");
            Objects.requireNonNull(iconKey, "iconKey");
            iconKey.ifPresent(value -> requireText(value, "iconKey"));
            if (expiresAtMillis < 0 || stacks <= 0) {
                throw new IllegalArgumentException("Некорректные expiry/stacks эффекта");
            }
        }
    }

    public record DotView(String id, String schoolId, int stacks, long expiresAtMillis, Optional<UUID> ownerId) {
        public DotView {
            requireText(id, "id");
            requireText(schoolId, "schoolId");
            Objects.requireNonNull(ownerId, "ownerId");
            if (stacks <= 0 || expiresAtMillis < 0) {
                throw new IllegalArgumentException("Некорректные stacks/expiry DoT");
            }
        }
    }

    public record CombatView(HudValue<Boolean> inCombat, HudValue<Long> combatWindowExpiresAtMillis) {
        public CombatView {
            Objects.requireNonNull(inCombat, "inCombat");
            Objects.requireNonNull(combatWindowExpiresAtMillis, "combatWindowExpiresAtMillis");
            validateNonNegative(combatWindowExpiresAtMillis, "combatWindowExpiresAtMillis");
        }
    }

    public record HudProfileView(HudValue<String> themeId, Set<String> enabledWidgetIds) {
        public HudProfileView {
            Objects.requireNonNull(themeId, "themeId");
            Objects.requireNonNull(enabledWidgetIds, "enabledWidgetIds");
            enabledWidgetIds = Set.copyOf(enabledWidgetIds);
            if (enabledWidgetIds.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("enabledWidgetIds не должны содержать null/пустые значения");
            }
        }
    }

    private static void validateNonNegative(HudValue<? extends Number> value, String name) {
        if (value.status() == DataStatus.AVAILABLE) {
            double number = value.value().doubleValue();
            if (!Double.isFinite(number) || number < 0.0d) {
                throw new IllegalArgumentException(name + " должен быть конечным неотрицательным числом");
            }
        }
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " не может быть пустым");
        }
    }
}

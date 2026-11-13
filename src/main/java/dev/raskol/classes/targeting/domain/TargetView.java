// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.domain;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/** Неизменяемый снимок выбранной цели для HUD и других read-only потребителей. */
public record TargetView(
        TargetIdentity identity,
        TargetCategory category,
        String displayName,
        OptionalInt level,
        OptionalDouble currentHealth,
        OptionalDouble maxHealth,
        Set<String> statusTags,
        long selectedAtMillis,
        long capturedAtMillis) {

    public TargetView {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(currentHealth, "currentHealth");
        Objects.requireNonNull(maxHealth, "maxHealth");
        Objects.requireNonNull(statusTags, "statusTags");

        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName не может быть пустым");
        }
        if (level.isPresent() && level.getAsInt() < 0) {
            throw new IllegalArgumentException("level не может быть отрицательным");
        }
        validateHealth(currentHealth, "currentHealth");
        validateHealth(maxHealth, "maxHealth");
        if (currentHealth.isPresent() != maxHealth.isPresent()) {
            throw new IllegalArgumentException("currentHealth и maxHealth должны быть доступны вместе");
        }
        if (currentHealth.isPresent() && maxHealth.getAsDouble() <= 0.0d) {
            throw new IllegalArgumentException("maxHealth должен быть положительным");
        }
        if (selectedAtMillis < 0 || capturedAtMillis < selectedAtMillis) {
            throw new IllegalArgumentException("Некорректные временные метки TargetView");
        }
        statusTags = Set.copyOf(statusTags);
        if (statusTags.stream().anyMatch(tag -> tag == null || tag.isBlank())) {
            throw new IllegalArgumentException("statusTags не должны содержать null или пустые значения");
        }
    }

    /** Возвращает нормализованное здоровье или пустое значение, если оно недоступно. */
    public OptionalDouble normalizedHealth() {
        if (currentHealth.isEmpty()) {
            return OptionalDouble.empty();
        }
        double ratio = currentHealth.getAsDouble() / maxHealth.getAsDouble();
        return OptionalDouble.of(Math.max(0.0d, Math.min(1.0d, ratio)));
    }

    private static void validateHealth(OptionalDouble value, String name) {
        if (value.isPresent()) {
            double amount = value.getAsDouble();
            if (!Double.isFinite(amount) || amount < 0.0d) {
                throw new IllegalArgumentException(name + " должен быть конечным неотрицательным числом");
            }
        }
    }
}

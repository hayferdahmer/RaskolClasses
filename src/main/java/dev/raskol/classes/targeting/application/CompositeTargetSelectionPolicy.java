// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.application;

import java.util.List;
import java.util.Objects;

import dev.raskol.classes.targeting.application.port.TargetSelectionCheck;
import dev.raskol.classes.targeting.application.port.TargetSelectionPolicy;

/**
 * Последовательно выполняет обязательные проверки и прекращает цепочку на первом отказе.
 * Ошибка отдельного провайдера трактуется как UNAVAILABLE: политика никогда не fail-open.
 */
public final class CompositeTargetSelectionPolicy implements TargetSelectionPolicy {

    private final List<TargetSelectionCheck> checks;

    public CompositeTargetSelectionPolicy(List<TargetSelectionCheck> checks) {
        Objects.requireNonNull(checks, "checks");
        this.checks = List.copyOf(checks);
        if (this.checks.isEmpty()) {
            throw new IllegalArgumentException("Для composite policy требуется хотя бы одна проверка");
        }
        if (this.checks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("checks не может содержать null");
        }
    }

    @Override
    public TargetSelectionPolicyResult evaluate(TargetInteractionInput input) {
        Objects.requireNonNull(input, "input");

        for (TargetSelectionCheck check : checks) {
            final TargetSelectionPolicyResult result;
            try {
                result = Objects.requireNonNull(check.evaluate(input), "selection check result");
            } catch (RuntimeException exception) {
                return TargetSelectionPolicyResult.unavailable(
                        TargetSelectionUnavailableReason.POLICY_PROVIDER_UNAVAILABLE);
            }

            if (!(result instanceof TargetSelectionPolicyResult.Accepted)) {
                return result;
            }
        }

        return TargetSelectionPolicyResult.accepted();
    }
}

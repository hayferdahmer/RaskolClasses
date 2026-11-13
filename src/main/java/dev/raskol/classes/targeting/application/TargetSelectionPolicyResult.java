// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.application;

import java.util.Objects;

/** Типизированное решение обязательной политики допуска к выбору цели. */
public sealed interface TargetSelectionPolicyResult
        permits TargetSelectionPolicyResult.Accepted,
                TargetSelectionPolicyResult.Rejected,
                TargetSelectionPolicyResult.Unavailable {

    record Accepted() implements TargetSelectionPolicyResult {}

    record Rejected(TargetSelectionRejectionReason reason) implements TargetSelectionPolicyResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record Unavailable(TargetSelectionUnavailableReason reason) implements TargetSelectionPolicyResult {
        public Unavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }

    static TargetSelectionPolicyResult accepted() {
        return new Accepted();
    }

    static TargetSelectionPolicyResult rejected(TargetSelectionRejectionReason reason) {
        return new Rejected(reason);
    }

    static TargetSelectionPolicyResult unavailable(TargetSelectionUnavailableReason reason) {
        return new Unavailable(reason);
    }
}

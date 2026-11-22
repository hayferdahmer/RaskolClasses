// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.application;

import java.util.Objects;

import dev.raskol.classes.targeting.domain.TargetSelection;

/** Результат команды выбора без утечки внутренних исключений и Bukkit-типов. */
public sealed interface TargetSelectionOutcome
        permits TargetSelectionOutcome.Selected,
                TargetSelectionOutcome.AlreadySelected,
                TargetSelectionOutcome.Rejected,
                TargetSelectionOutcome.Unavailable {

    record Selected(TargetSelection selection) implements TargetSelectionOutcome {
        public Selected {
            Objects.requireNonNull(selection, "selection");
        }
    }

    record AlreadySelected(TargetSelection selection) implements TargetSelectionOutcome {
        public AlreadySelected {
            Objects.requireNonNull(selection, "selection");
        }
    }

    record Rejected(TargetSelectionRejectionReason reason) implements TargetSelectionOutcome {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record Unavailable(TargetSelectionUnavailableReason reason) implements TargetSelectionOutcome {
        public Unavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }
}

// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.domain;

import java.util.Objects;
import java.util.UUID;

/** Эфемерный факт выбора цели игроком в текущей сессии. */
public record TargetSelection(
        UUID ownerId, TargetIdentity target, long selectedAtMillis, TargetSelectionSource source) {

    public TargetSelection {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        if (selectedAtMillis < 0) {
            throw new IllegalArgumentException("selectedAtMillis не может быть отрицательным");
        }
    }
}

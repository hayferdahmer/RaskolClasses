// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.application;

import java.util.Objects;
import java.util.UUID;

import dev.raskol.classes.targeting.domain.TargetInteractionHand;
import dev.raskol.classes.targeting.domain.TargetSelectionSource;

/** Bukkit-независимый вход явного взаимодействия для выбора цели. */
public record TargetInteractionInput(
        UUID actorId,
        UUID candidateId,
        TargetSelectionSource source,
        TargetInteractionHand hand) {

    public TargetInteractionInput {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(candidateId, "candidateId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(hand, "hand");
    }
}

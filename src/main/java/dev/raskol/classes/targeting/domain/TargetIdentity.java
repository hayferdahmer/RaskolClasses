// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.domain;

import java.util.Objects;
import java.util.UUID;

/** Стабильная идентичность выбранной игровой сущности. */
public record TargetIdentity(UUID entityId) {

    public TargetIdentity {
        Objects.requireNonNull(entityId, "entityId");
    }
}

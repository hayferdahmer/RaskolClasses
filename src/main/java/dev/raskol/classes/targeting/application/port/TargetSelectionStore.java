// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.application.port;

import java.util.Optional;
import java.util.UUID;

import dev.raskol.classes.targeting.domain.TargetSelection;

/** Порт эфемерного хранилища выбранных целей по владельцу-сессии. */
public interface TargetSelectionStore {

    Optional<TargetSelection> findByOwner(UUID ownerId);

    void save(TargetSelection selection);

    Optional<TargetSelection> remove(UUID ownerId);

    void clear();
}

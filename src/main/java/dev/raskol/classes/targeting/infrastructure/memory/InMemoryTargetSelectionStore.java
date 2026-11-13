// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.infrastructure.memory;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import dev.raskol.classes.targeting.application.port.TargetSelectionStore;
import dev.raskol.classes.targeting.domain.TargetSelection;

/** Потокобезопасное in-memory хранилище locked target для активных сессий. */
public final class InMemoryTargetSelectionStore implements TargetSelectionStore {

    private final ConcurrentMap<UUID, TargetSelection> selections = new ConcurrentHashMap<>();

    @Override
    public Optional<TargetSelection> findByOwner(UUID ownerId) {
        return Optional.ofNullable(selections.get(Objects.requireNonNull(ownerId, "ownerId")));
    }

    @Override
    public void save(TargetSelection selection) {
        Objects.requireNonNull(selection, "selection");
        selections.put(selection.ownerId(), selection);
    }

    @Override
    public Optional<TargetSelection> remove(UUID ownerId) {
        return Optional.ofNullable(selections.remove(Objects.requireNonNull(ownerId, "ownerId")));
    }

    @Override
    public void clear() {
        selections.clear();
    }
}

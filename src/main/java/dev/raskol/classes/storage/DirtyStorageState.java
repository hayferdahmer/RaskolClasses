// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.storage;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Состояние несохранённых изменений общего YAML-хранилища. */
public final class DirtyStorageState {

    private boolean dirty;
    private final Set<UUID> dirtyPlayers = new HashSet<>();

    public void markDirty() {
        dirty = true;
    }

    public void markDirty(UUID playerId) {
        if (playerId != null) {
            dirtyPlayers.add(playerId);
        }
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean containsPlayer(UUID playerId) {
        return dirtyPlayers.contains(playerId);
    }

    public int dirtyPlayerCount() {
        return dirtyPlayers.size();
    }

    public void removePlayersIf(Predicate<UUID> predicate) {
        dirtyPlayers.removeIf(predicate);
    }

    public void clear() {
        dirtyPlayers.clear();
        dirty = false;
    }

    /** Очищает dirty-маркеры только после подтверждённой успешной записи. */
    public boolean flush(BooleanSupplier writer) {
        if (!dirty || !writer.getAsBoolean()) {
            return false;
        }
        clear();
        return true;
    }
}

// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.storage;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import dev.raskol.classes.character.profile.CharacterProfileStorage;

/**
 * Совместимый фасад Spec 2.0 над единым characters.yml.
 *
 * Специализация, ранги, прогресс персонажа и квитанции квестовых наград
 * сериализуются одним CharacterProfileStorage в общий атомарный снимок.
 */
public final class Spec2Storage {

    private final CharacterProfileStorage profiles;

    public Spec2Storage(CharacterProfileStorage profiles) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    public String getMain(UUID uuid) {
        return profiles.getSpecializationMain(uuid);
    }

    public void setMain(UUID uuid, String specId) {
        profiles.setSpecializationMain(uuid, specId);
    }

    public Map<String, Integer> getRanks(UUID uuid, String treeId) {
        return profiles.getSpecializationRanks(uuid, treeId);
    }

    public void setRanks(UUID uuid, String treeId, Map<String, Integer> ranks) {
        profiles.setSpecializationRanks(uuid, treeId, ranks);
    }

    /** Возвращает id деревьев, по которым у игрока сохранены ранги. */
    public List<String> storedTreeIds(UUID uuid) {
        return profiles.storedSpecializationTreeIds(uuid);
    }

    /** Удаляет сохранённые ранги одного дерева. */
    public void removeTree(UUID uuid, String treeId) {
        profiles.removeSpecializationTree(uuid, treeId);
    }

    /** Сохраняет общий снимок персонажей и специализаций. */
    public boolean save() {
        return profiles.save();
    }

    public boolean saveIfDirty() {
        return profiles.saveIfDirty();
    }

    public boolean isDirty() {
        return profiles.isDirty();
    }
}

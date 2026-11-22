// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.model;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.spec.Spec;

/**
 * Чистая политика согласования сохранённой специализации с текущим классом.
 * Неопределённый класс никогда не считается основанием для удаления данных.
 */
public final class Spec2ClassState {

    public record ReconcilePlan(boolean classResolved, boolean clearMainSpec, List<String> foreignTreeIds) {
        public ReconcilePlan {
            foreignTreeIds = List.copyOf(foreignTreeIds);
        }
    }

    private Spec2ClassState() {}

    public static List<String> treesOf(PlayerClass playerClass) {
        if (playerClass == null) {
            return List.of();
        }
        return java.util.Arrays.stream(Spec.forClass(playerClass)).map(Spec::id).toList();
    }

    public static boolean isMainSpecCompatible(PlayerClass currentClass, String mainSpec) {
        if (currentClass == null) {
            return false;
        }
        Spec spec = Spec.fromId(mainSpec);
        return spec != null && spec.playerClass() == currentClass;
    }

    public static ReconcilePlan plan(
            PlayerClass currentClass, String mainSpec, Collection<String> storedTreeIds) {
        if (currentClass == null) {
            return new ReconcilePlan(false, false, List.of());
        }

        Set<String> allowedTrees = Set.copyOf(treesOf(currentClass));
        boolean clearMain = mainSpec != null && !allowedTrees.contains(mainSpec);
        List<String> foreignTrees = storedTreeIds == null
                ? List.of()
                : storedTreeIds.stream().filter(treeId -> !allowedTrees.contains(treeId)).toList();
        return new ReconcilePlan(true, clearMain, foreignTrees);
    }
}

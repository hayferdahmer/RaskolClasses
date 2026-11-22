// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.classsystem;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Единый контракт класса через LuckPerms: ровно одна эффективная class_* группа,
 * и она должна быть primary-группой пользователя.
 */
public final class ClassGroupResolver {

    public enum Status {
        NO_CLASS,
        RESOLVED,
        CONFLICT
    }

    public record Resolution(Status status, PlayerClass playerClass, Set<String> classGroups) {
        public Resolution {
            classGroups = Set.copyOf(classGroups);
        }
    }

    private static final String PREFIX = "class_";

    private ClassGroupResolver() {}

    public static Resolution resolve(String primaryGroup, Collection<String> inheritedGroupNames) {
        String primary = normalize(primaryGroup);
        Set<String> classGroups = new TreeSet<>();

        addClassGroup(classGroups, primary);
        if (inheritedGroupNames != null) {
            for (String groupName : inheritedGroupNames) {
                addClassGroup(classGroups, normalize(groupName));
            }
        }

        if (classGroups.isEmpty()) {
            return new Resolution(Status.NO_CLASS, null, Set.of());
        }
        if (classGroups.size() != 1 || !classGroups.contains(primary)) {
            return new Resolution(Status.CONFLICT, null, classGroups);
        }

        PlayerClass playerClass = PlayerClass.fromLuckPermsGroup(primary);
        if (playerClass == null) {
            return new Resolution(Status.CONFLICT, null, classGroups);
        }
        return new Resolution(Status.RESOLVED, playerClass, classGroups);
    }

    private static void addClassGroup(Set<String> groups, String groupName) {
        if (groupName != null && groupName.startsWith(PREFIX)) {
            groups.add(groupName);
        }
    }

    private static String normalize(String groupName) {
        return groupName == null ? null : groupName.toLowerCase(Locale.ROOT);
    }
}

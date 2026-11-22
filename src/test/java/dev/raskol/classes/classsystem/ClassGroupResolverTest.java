// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.classsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ClassGroupResolverTest {

    @Test
    void resolvesSinglePrimaryClassGroup() {
        ClassGroupResolver.Resolution result =
                ClassGroupResolver.resolve("class_mage", List.of("class_mage", "default"));

        assertEquals(ClassGroupResolver.Status.RESOLVED, result.status());
        assertEquals(PlayerClass.MAGE, result.playerClass());
        assertEquals(Set.of("class_mage"), result.classGroups());
    }

    @Test
    void reportsNoClassWhenNoClassGroupExists() {
        ClassGroupResolver.Resolution result =
                ClassGroupResolver.resolve("default", List.of("default", "vip"));

        assertEquals(ClassGroupResolver.Status.NO_CLASS, result.status());
        assertNull(result.playerClass());
    }

    @Test
    void rejectsClassGroupThatIsNotPrimary() {
        ClassGroupResolver.Resolution result =
                ClassGroupResolver.resolve("default", List.of("default", "class_mage"));

        assertEquals(ClassGroupResolver.Status.CONFLICT, result.status());
        assertNull(result.playerClass());
    }

    @Test
    void rejectsMultipleClassGroupsInsteadOfChoosingAnArbitraryOne() {
        ClassGroupResolver.Resolution result =
                ClassGroupResolver.resolve("class_mage", List.of("class_mage", "class_warrior"));

        assertEquals(ClassGroupResolver.Status.CONFLICT, result.status());
        assertNull(result.playerClass());
    }

    @Test
    void rejectsUnknownClassGroup() {
        ClassGroupResolver.Resolution result =
                ClassGroupResolver.resolve("class_unknown", List.of("class_unknown"));

        assertEquals(ClassGroupResolver.Status.CONFLICT, result.status());
        assertNull(result.playerClass());
    }

    @Test
    void normalizesGroupNamesCaseInsensitively() {
        ClassGroupResolver.Resolution result =
                ClassGroupResolver.resolve("CLASS_ROGUE", List.of("class_rogue"));

        assertEquals(ClassGroupResolver.Status.RESOLVED, result.status());
        assertEquals(PlayerClass.ROGUE, result.playerClass());
    }
}

// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.ability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AbilityAccessPolicy: уровень и узлы дерева")
class AbilityAccessPolicyTest {

    @Test
    @DisplayName("перенесённая способность открывается узлом, а не старым уровнем 55/60")
    void treeNodeReplacesLegacyLevelGate() {
        AbilityDef transferable = new AbilityDef("fenrir_blood", "Кровь Фенрира", 4, 55, 30, 25_000L);

        assertFalse(AbilityAccessPolicy.isUnlocked(transferable, 20, true, false));
        assertTrue(AbilityAccessPolicy.isUnlocked(transferable, 20, true, true));
    }

    @Test
    @DisplayName("обычная способность до переноса продолжает использовать уровень")
    void ordinaryAbilityKeepsLevelGate() {
        AbilityDef ordinary = new AbilityDef("balder_skin", "Шкура Бальдра", 2, 25, 25, 30_000L);

        assertFalse(AbilityAccessPolicy.isUnlocked(ordinary, 24, false, false));
        assertTrue(AbilityAccessPolicy.isUnlocked(ordinary, 25, false, false));
    }

    @Test
    @DisplayName("для tree-gated способности старый уровень не открывает способность без узла")
    void legacyLevelCannotBypassTreeNode() {
        AbilityDef transferable = new AbilityDef("ragnarok", "Рагнарёк", 5, 60, 60, 60_000L);

        assertFalse(AbilityAccessPolicy.isUnlocked(transferable, 60, true, false));
    }
}

// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.ability;

import java.util.Objects;

/**
 * Единая политика доступа к способности.
 *
 * Для способностей, открываемых деревом талантов, ранг узла является
 * единственным гейтом доступа. Старый unlockLevel остаётся только для
 * способностей, которые ещё не переведены в дерево.
 */
public final class AbilityAccessPolicy {

    private AbilityAccessPolicy() {}

    public static boolean isUnlocked(
            AbilityDef definition, int characterLevel, boolean treeGated, boolean treeUnlocked) {
        Objects.requireNonNull(definition, "definition");
        return treeGated ? treeUnlocked : characterLevel >= definition.unlockLevel();
    }
}

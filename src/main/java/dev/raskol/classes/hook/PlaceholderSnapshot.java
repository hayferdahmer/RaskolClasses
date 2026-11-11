// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hook;

import java.util.Map;

/**
 * Неизменяемый снимок значений плейсхолдеров одного игрока.
 */
record PlaceholderSnapshot(Map<String, String> raskolValues, Map<String, String> crownValues) {

    PlaceholderSnapshot {
        raskolValues = Map.copyOf(raskolValues);
        crownValues = Map.copyOf(crownValues);
    }

    String raskolValue(String parameter) {
        return raskolValues.get(parameter);
    }

    String crownValue(String parameter) {
        return crownValues.get(parameter);
    }
}

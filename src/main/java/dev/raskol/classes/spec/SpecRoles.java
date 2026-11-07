// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec;

import java.util.Map;

/**
 * 1.14.0 «Спек 2.0»: роль каждой из 18 специализаций (дизайн-док, раздел 6).
 * guard = TANK; discipline/holy = HEALER; остальные = FIGHTER (включая shadow).
 * 1.14.0 (Б8.2-fix): ключ чернокнижника унифицирован с enum/деревьями/конфигом —
 *         "affliction" (ошибочный "witchcraft" убран).
 */
public final class SpecRoles {

    private static final Map<String, SpecRole> ROLES = Map.ofEntries(
            Map.entry("arms", SpecRole.FIGHTER),
            Map.entry("fury", SpecRole.FIGHTER),
            Map.entry("guard", SpecRole.TANK),
            Map.entry("marksmanship", SpecRole.FIGHTER),
            Map.entry("survival", SpecRole.FIGHTER),
            Map.entry("beastmaster", SpecRole.FIGHTER),
            Map.entry("discipline", SpecRole.HEALER),
            Map.entry("holy", SpecRole.HEALER),
            Map.entry("shadow", SpecRole.FIGHTER),
            Map.entry("arcane", SpecRole.FIGHTER),
            Map.entry("fire", SpecRole.FIGHTER),
            Map.entry("frost", SpecRole.FIGHTER),
            Map.entry("assassination", SpecRole.FIGHTER),
            Map.entry("outlaw", SpecRole.FIGHTER),
            Map.entry("subtlety", SpecRole.FIGHTER),
            Map.entry("affliction", SpecRole.FIGHTER),
            Map.entry("destruction", SpecRole.FIGHTER),
            Map.entry("demonology", SpecRole.FIGHTER));

    private SpecRoles() {}

    public static SpecRole roleOf(String specId) {
        return ROLES.getOrDefault(specId, SpecRole.FIGHTER);
    }

    public static boolean isKnown(String specId) {
        return ROLES.containsKey(specId);
    }
}

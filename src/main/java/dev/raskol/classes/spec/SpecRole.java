// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec;

/**
 * 1.14.0 (почва закладывается в 1.13.0): роль специализации «Спек 2.0».
 * Каждая из 18 специализаций (6 классов × 3) несёт одну основную роль:
 *  - FIGHTER — урон/давление;
 *  - TANK    — поглощение урона, контроль агро, защита союзников;
 *  - HEALER  — лечение, снятие CC, поддержание пати.
 * В 1.13.0 тип не используется боевой логикой — только как фундамент модели
 * и для будущих конфигов/Книги класса.
 */
public enum SpecRole {
    FIGHTER("Боец"),
    TANK("Танк"),
    HEALER("Лекарь");

    private final String ruName;

    SpecRole(String ruName) {
        this.ruName = ruName;
    }

    public String ruName() {
        return ruName;
    }

    public String id() {
        return name();
    }
}

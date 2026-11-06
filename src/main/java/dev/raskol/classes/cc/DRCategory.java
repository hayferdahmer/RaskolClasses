// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.cc;

/**
 * 1.13.0: категории убывающей отдачи (DR). CC-типы одной категории делят
 * общий счётчик стеков: стан и отброс нельзя чередовать в обход DR,
 * сайленс и дезарм — тоже одна категория.
 */
public enum DRCategory {
    STUN("Оцепенение"),
    FEAR("Ужас"),
    ROOT("Оковы"),
    SILENCE("Немота"),
    SLOW("Вязь"),
    BLIND("Слепота");

    private final String ruName;

    DRCategory(String ruName) {
        this.ruName = ruName;
    }

    public String ruName() {
        return ruName;
    }

    public String id() {
        return name();
    }
}

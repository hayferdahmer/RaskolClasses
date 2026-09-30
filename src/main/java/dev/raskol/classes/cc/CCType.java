// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.cc;

import java.util.Locale;

/**
 * 1.13.0: типы контроля (CC) в лоре «РАСКОЛ | ДВЕ КОРОНЫ».
 *  - category — категория DR (общий счётчик убывающей отдачи);
 *  - instant — мгновенный эффект (KNOCKBACK): не статус, не живёт в реестре;
 *  - defaultTicks — дефолтная длительность (конфиг cc.types.<ID>.duration-ticks переопределяет);
 *  - breaksOnDamage / interruptible — дефолты поведения (конфиг переопределяет).
 */
public enum CCType {

    STUN(DRCategory.STUN, false, 60, false, true, "Оцепенение"),
    ROOT(DRCategory.ROOT, false, 80, true, false, "Оковы"),
    SILENCE(DRCategory.SILENCE, false, 100, false, true, "Печать Молчания"),
    DISARM(DRCategory.SILENCE, false, 80, false, false, "Обезоруживание"),
    FEAR(DRCategory.FEAR, false, 60, true, true, "Ужас"),
    CHARM(DRCategory.FEAR, false, 60, false, true, "Одержимость"),
    SLOW(DRCategory.SLOW, false, 120, false, false, "Вязь"),
    BLIND(DRCategory.BLIND, false, 100, false, false, "Слепота"),
    KNOCKBACK(DRCategory.STUN, true, 0, false, false, "Отброс");

    private final DRCategory category;
    private final boolean instant;
    private final int defaultTicks;
    private final boolean breaksOnDamage;
    private final boolean interruptible;
    private final String ruName;

    CCType(DRCategory category, boolean instant, int defaultTicks,
           boolean breaksOnDamage, boolean interruptible, String ruName) {
        this.category = category;
        this.instant = instant;
        this.defaultTicks = defaultTicks;
        this.breaksOnDamage = breaksOnDamage;
        this.interruptible = interruptible;
        this.ruName = ruName;
    }

    public DRCategory category() { return category; }
    public boolean instant() { return instant; }
    public int defaultTicks() { return defaultTicks; }
    public boolean breaksOnDamage() { return breaksOnDamage; }
    public boolean interruptible() { return interruptible; }
    public String ruName() { return ruName; }
    public String id() { return name(); }

    public static CCType fromId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

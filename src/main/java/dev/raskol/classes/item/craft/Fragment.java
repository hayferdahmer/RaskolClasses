// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.craft;

import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.item.model.ItemRarity;
import org.bukkit.Material;

/**
 * 1.15.4: фрагмент — расходная валюта крафта (RaskolGear-стиль).
 *
 * Четыре вида:
 *  - WEAPON / ARMOR — осколки по типу снаряжения, три тира (COMMON/RARE/EPIC);
 *  - ESSENCE — классовая эссенция (6 классов), тир не используется;
 *  - RELIC — реликвия (LEGENDARY/MYTHIC-источники), тир не используется.
 *
 * Фрагменты НЕ продаются (нет sell-цены), существуют только как мост
 * «разбор → крафт»; дроп с боссов/элиты подключает RaskolMobs через ItemsApi.
 */
public record Fragment(String id, Kind kind, ItemRarity tier, PlayerClass fragmentClass,
                       Material material, String displayName) {

    public enum Kind { WEAPON, ARMOR, ESSENCE, RELIC }

    public Fragment {
        if (id == null || !id.matches("[a-z0-9_]{3,48}")) {
            throw new IllegalArgumentException("Invalid fragment id: " + id);
        }
        if (kind == null || material == null || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Invalid fragment: " + id);
        }
        if ((kind == Kind.WEAPON || kind == Kind.ARMOR) && tier == null) {
            throw new IllegalArgumentException("Weapon/armor fragment requires tier: " + id);
        }
        if (kind == Kind.ESSENCE && fragmentClass == null) {
            throw new IllegalArgumentException("Essence fragment requires class: " + id);
        }
    }
}

// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.item.model.WeaponType;

import java.util.EnumMap;
import java.util.Map;

/**
 * 1.15.2: матрица владения оружием (weapon mastery).
 *
 * Каждый класс имеет три уровня владения типами оружия:
 *   - ×1.00 (native)    — основное оружие класса, полный урон;
 *   - ×0.75 (familiar)  — знакомое оружие, сниженный урон;
 *   - ×0.50 (foreign)   — чужое оружие, минимальный урон.
 *
 * Матрица 9 типов × 6 классов:
 *   WARRIOR:  SWORD(1.0) AXE(1.0) MACE(1.0) POLEARM(1.0) DAGGER(0.75) BOW(0.5) CROSSBOW(0.5) STAFF(0.5) WAND(0.5)
 *   HUNTER:   BOW(1.0) CROSSBOW(1.0) DAGGER(0.75) AXE(0.75) MACE(0.5) POLEARM(0.5) SWORD(0.5) STAFF(0.5) WAND(0.5)
 *   ROGUE:    DAGGER(1.0) SWORD(0.75) AXE(0.5) MACE(0.5) POLEARM(0.5) BOW(0.5) CROSSBOW(0.5) STAFF(0.5) WAND(0.5)
 *   MAGE:     STAFF(1.0) WAND(1.0) DAGGER(0.5) SWORD(0.5) AXE(0.5) MACE(0.5) POLEARM(0.5) BOW(0.5) CROSSBOW(0.5)
 *   PRIEST:   MACE(1.0) STAFF(0.75) WAND(0.75) DAGGER(0.5) SWORD(0.5) AXE(0.5) POLEARM(0.5) BOW(0.5) CROSSBOW(0.5)
 *   WARLOCK:  WAND(1.0) STAFF(0.75) DAGGER(0.5) SWORD(0.5) AXE(0.5) MACE(0.5) POLEARM(0.5) BOW(0.5) CROSSBOW(0.5)
 *
 * Pure-функция без side-effect; вызывается из applyOutgoingOffense для физического урона.
 * Магическое оружие (STAFF/WAND) уже даёт MAGIC-автоатаку; mastery применяется только
 * к PHYSICAL-каналу (проверка type == PHYSICAL в caller'е).
 */
public final class WeaponMastery {

    /** Множитель ×1.00: основное оружие класса. */
    public static final double NATIVE = 1.00;
    /** Множитель ×0.75: знакомое оружие. */
    public static final double FAMILIAR = 0.75;
    /** Множитель ×0.50: чужое оружие. */
    public static final double FOREIGN = 0.50;

    private static final Map<PlayerClass, Map<WeaponType, Double>> MATRIX = buildMatrix();

    private WeaponMastery() {
    }

    /**
     * Возвращает множитель владения для пары (класс, тип оружия).
     * Возвращает FOREIGN (0.5) для null-параметров (defensive programming).
     */
    public static double masteryMultiplier(PlayerClass pc, WeaponType weaponType) {
        if (pc == null || weaponType == null) {
            return FOREIGN;
        }
        Map<WeaponType, Double> row = MATRIX.get(pc);
        if (row == null) {
            return FOREIGN;
        }
        return row.getOrDefault(weaponType, FOREIGN);
    }

    /** Категория владения для отладки: NATIVE / FAMILIAR / FOREIGN. */
    public static String masteryLevel(PlayerClass pc, WeaponType weaponType) {
        double mult = masteryMultiplier(pc, weaponType);
        if (Math.abs(mult - NATIVE) < 1e-9) {
            return "NATIVE";
        }
        if (Math.abs(mult - FAMILIAR) < 1e-9) {
            return "FAMILIAR";
        }
        return "FOREIGN";
    }

    private static Map<PlayerClass, Map<WeaponType, Double>> buildMatrix() {
        Map<PlayerClass, Map<WeaponType, Double>> matrix = new EnumMap<>(PlayerClass.class);

        Map<WeaponType, Double> warrior = new EnumMap<>(WeaponType.class);
        warrior.put(WeaponType.SWORD, NATIVE);
        warrior.put(WeaponType.AXE, NATIVE);
        warrior.put(WeaponType.MACE, NATIVE);
        warrior.put(WeaponType.POLEARM, NATIVE);
        warrior.put(WeaponType.DAGGER, FAMILIAR);
        warrior.put(WeaponType.BOW, FOREIGN);
        warrior.put(WeaponType.CROSSBOW, FOREIGN);
        warrior.put(WeaponType.STAFF, FOREIGN);
        warrior.put(WeaponType.WAND, FOREIGN);
        matrix.put(PlayerClass.WARRIOR, Map.copyOf(warrior));

        Map<WeaponType, Double> hunter = new EnumMap<>(WeaponType.class);
        hunter.put(WeaponType.BOW, NATIVE);
        hunter.put(WeaponType.CROSSBOW, NATIVE);
        hunter.put(WeaponType.DAGGER, FAMILIAR);
        hunter.put(WeaponType.AXE, FAMILIAR);
        hunter.put(WeaponType.MACE, FOREIGN);
        hunter.put(WeaponType.POLEARM, FOREIGN);
        hunter.put(WeaponType.SWORD, FOREIGN);
        hunter.put(WeaponType.STAFF, FOREIGN);
        hunter.put(WeaponType.WAND, FOREIGN);
        matrix.put(PlayerClass.HUNTER, Map.copyOf(hunter));

        Map<WeaponType, Double> rogue = new EnumMap<>(WeaponType.class);
        rogue.put(WeaponType.DAGGER, NATIVE);
        rogue.put(WeaponType.SWORD, FAMILIAR);
        rogue.put(WeaponType.AXE, FOREIGN);
        rogue.put(WeaponType.MACE, FOREIGN);
        rogue.put(WeaponType.POLEARM, FOREIGN);
        rogue.put(WeaponType.BOW, FOREIGN);
        rogue.put(WeaponType.CROSSBOW, FOREIGN);
        rogue.put(WeaponType.STAFF, FOREIGN);
        rogue.put(WeaponType.WAND, FOREIGN);
        matrix.put(PlayerClass.ROGUE, Map.copyOf(rogue));

        Map<WeaponType, Double> mage = new EnumMap<>(WeaponType.class);
        mage.put(WeaponType.STAFF, NATIVE);
        mage.put(WeaponType.WAND, NATIVE);
        mage.put(WeaponType.DAGGER, FOREIGN);
        mage.put(WeaponType.SWORD, FOREIGN);
        mage.put(WeaponType.AXE, FOREIGN);
        mage.put(WeaponType.MACE, FOREIGN);
        mage.put(WeaponType.POLEARM, FOREIGN);
        mage.put(WeaponType.BOW, FOREIGN);
        mage.put(WeaponType.CROSSBOW, FOREIGN);
        matrix.put(PlayerClass.MAGE, Map.copyOf(mage));

        Map<WeaponType, Double> priest = new EnumMap<>(WeaponType.class);
        priest.put(WeaponType.MACE, NATIVE);
        priest.put(WeaponType.STAFF, FAMILIAR);
        priest.put(WeaponType.WAND, FAMILIAR);
        priest.put(WeaponType.DAGGER, FOREIGN);
        priest.put(WeaponType.SWORD, FOREIGN);
        priest.put(WeaponType.AXE, FOREIGN);
        priest.put(WeaponType.POLEARM, FOREIGN);
        priest.put(WeaponType.BOW, FOREIGN);
        priest.put(WeaponType.CROSSBOW, FOREIGN);
        matrix.put(PlayerClass.PRIEST, Map.copyOf(priest));

        Map<WeaponType, Double> warlock = new EnumMap<>(WeaponType.class);
        warlock.put(WeaponType.WAND, NATIVE);
        warlock.put(WeaponType.STAFF, FAMILIAR);
        warlock.put(WeaponType.DAGGER, FOREIGN);
        warlock.put(WeaponType.SWORD, FOREIGN);
        warlock.put(WeaponType.AXE, FOREIGN);
        warlock.put(WeaponType.MACE, FOREIGN);
        warlock.put(WeaponType.POLEARM, FOREIGN);
        warlock.put(WeaponType.BOW, FOREIGN);
        warlock.put(WeaponType.CROSSBOW, FOREIGN);
        matrix.put(PlayerClass.WARLOCK, Map.copyOf(warlock));

        return Map.copyOf(matrix);
    }
}

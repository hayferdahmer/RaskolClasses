// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.classsystem.PlayerClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1.14.0 (Батч 11.1.1): реестр ПЕРЕНОСИМЫХ китовых способностей.
 *
 * Slots-долг (Батч 11.1): базовый кит ужимается до 3 способностей (slots 1–3),
 * а slots 4–5 каждого класса переезжают в деревья путей как узлы unlock_ability.
 * Этот класс — ЕДИНСТВЕННЫЙ источник истины о том, КАКИЕ 12 id являются
 * переносимыми и какой legacy-slot (4 или 5) они занимали в старом ките.
 *
 * ДИЗАЙН-РЕШЕНИЕ 11.1.1: признак переносимости НЕ вводится полем в AbilityDef
 * (record → breaking change для 99 вызовов new AbilityDef). Вместо этого —
 * внешний id→legacySlot-реестр; AbilityRegistry.read-only индексирует его и
 * отдаёт хелперы isTransferable/transferableSlot/getBySlotOrTree.
 *
 * ПЕРЕХОДНЫЙ ИНВАРИАНТ (валидируется selftest-чеками 95/96):
 *   пока DEFAULTS не урезан (11.1.1–11.1.2), каждый переносимый id присутствует
 *   в DEFAULTS своего класса ровно на legacySlot, и getBySlotOrTree == getBySlot.
 *   В 11.1.3 (резка DEFAULTS) чек 95 будет ПЕРЕПИСАН на «id имеет unlock_ability-узел
 *   в деревьях класса», а чек 96 — на «getBySlotOrTree резолвит slot 4–5 через
 *   Spec2Service.hasUnlocked, getBySlot по-прежнему null». Это управляемая миграция
 *   теста вместе с кодом, а не тихая регрессия.
 *
 * Не читает конфиг и не зависит от Bukkit-рантайма → safe для headless-валидатора.
 */
public final class TransferableAbilities {

    private TransferableAbilities() {
    }

    /** Одна переносимая способность: класс, id и legacy-slot (4/5) в старом ките. */
    public record Entry(PlayerClass pc, String id, int legacySlot) {
    }

    /** id → запись (порядок вставки = порядок обхода для детерминизма). */
    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();
    /** класс → ровно 2 переносимые записи (slots 4 и 5). */
    private static final Map<PlayerClass, List<Entry>> BY_CLASS = new EnumMap<>(PlayerClass.class);

    static {
        // ВОИН: slot4 = Кровь Фенрира (хил), slot5 = Рагнарёк (execute-ульт)
        reg(PlayerClass.WARRIOR, "fenrir_blood", 4);
        reg(PlayerClass.WARRIOR, "ragnarok", 5);
        // ОХОТНИК: slot4 = Веер стрел, slot5 = Дождь стрел (AoE)
        reg(PlayerClass.HUNTER, "arrow_fan", 4);
        reg(PlayerClass.HUNTER, "arrow_rain", 5);
        // ЖРЕЦ: slot4 = Круг Элизия (AoE-хил), slot5 = Кара Небес (execute)
        reg(PlayerClass.PRIEST, "circle_elysium", 4);
        reg(PlayerClass.PRIEST, "wrath_heaven", 5);
        // МАГ: slot4 = Эгида Афины (маг-щит), slot5 = Гнев Зевса (execute)
        reg(PlayerClass.MAGE, "athena_aegis", 4);
        reg(PlayerClass.MAGE, "zeus_wrath", 5);
        // РАЗБОЙНИК: slot4 = Яд Борджа, slot5 = Танец теней (бурст)
        reg(PlayerClass.ROGUE, "borgia_poison", 4);
        reg(PlayerClass.ROGUE, "shadow_dance", 5);
        // ЧЕРНОКНИЖНИК: slot4 = Небытие (диспел), slot5 = Раскол Души (канал-ульт)
        reg(PlayerClass.WARLOCK, "unwriting", 4);
        reg(PlayerClass.WARLOCK, "soul_rift", 5);

        // Заморозка per-class списков в неизменяемые (реестр immutable после статики).
        for (PlayerClass pc : PlayerClass.values()) {
            BY_CLASS.put(pc, List.copyOf(BY_CLASS.getOrDefault(pc, List.of())));
        }
    }

    private static void reg(PlayerClass pc, String id, int legacySlot) {
        Entry e = new Entry(pc, id, legacySlot);
        BY_ID.put(id, e);
        BY_CLASS.computeIfAbsent(pc, k -> new ArrayList<>()).add(e);
    }

    /** Идентификатор способности переносимая (станет unlock_ability-узлом дерева)? */
    public static boolean isTransferable(String id) {
        return id != null && BY_ID.containsKey(id);
    }

    /** Legacy-slot (4/5) переносимой id; -1 если id не переносимая. */
    public static int legacySlot(String id) {
        Entry e = BY_ID.get(id);
        return e == null ? -1 : e.legacySlot();
    }

    /** Запись по id или null. */
    public static Entry get(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** Ровно 2 переносимые записи класса (slots 4 и 5); пустой список для неизвестного класса. */
    public static List<Entry> forClass(PlayerClass pc) {
        return BY_CLASS.getOrDefault(pc, List.of());
    }

    /** Все 12 записей (детерминированный порядок вставки). */
    public static Collection<Entry> all() {
        return List.copyOf(BY_ID.values());
    }

    /** Неизменяемое множество всех переносимых id (для прощения сиротства в реестре). */
    public static Set<String> allIds() {
        return Set.copyOf(BY_ID.keySet());
    }

    /** Общее число переносимых (инвариант = 12; валидируется чеком 95). */
    public static int size() {
        return BY_ID.size();
    }
}

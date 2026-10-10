// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability.passive;

import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.event.CustomHealEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.UUID;

/**
 * 1.11.4 (P1): контракт пассивки класса. Один класс = один файл-реализация;
 * PassiveListener = тонкий диспетчер без логики.
 *
 * 1.14.7 (Sprint 4, P1-1): добавлен канал CustomHealEvent (onCustomHealOut).
 * Исходящее лечение китов имеет атрибуцию целителя только на этом пути —
 * ванильный regain (EntityRegainHealthEvent: еда, зелья, regen-эффекты)
 * атрибуции не несёт и поэтому не диспетчерится по пассивкам.
 * onHealOut оставлен как @Deprecated: реализация в коде есть только в
 * PriestPassives и перенесена на onCustomHealOut.
 */
public interface ClassPassive {

    PlayerClass playerClass();

    /** Атакующий этого класса нанёс урон (событие не отменено). */
    default void onDamageOut(EntityDamageByEntityEvent event, Player attacker,
                             LivingEntity target, double damage) {
    }

    /** Игрок этого класса получил урон от сущности. */
    default void onDamageIn(EntityDamageByEntityEvent event, Player victim, double damage) {
    }

    /**
     * Пассивка этого класса как ЦЕЛИТЕЛЬ на пути лечения китов (CustomHealEvent).
     * Вызывается из PassiveListener.onCustomHeal (приоритет HIGHEST, ДО spec2-множителей).
     * Реализация может модифицировать event.getAmount() — множитель применяется к базовому
     * heal-amount, далее Spec2RoleListener.onCustomHeal умножит на heal_out_pct × HEALER.
     */
    default void onCustomHealOut(CustomHealEvent event, Player healer) {
    }

    /**
     * @deprecated С 1.14.7 (Sprint 4, P1-1) диспетчер для EntityRegainHealthEvent
     *             удалён — ванильный regain не несёт атрибуции целителя, и применение
     *             пассивок «Жрец лечит → +15%» к еде/зельям было misattribution-багом.
     *             Логика «Благодати» (единственная реальная реализация) перенесена
     *             на onCustomHealOut. Метод оставлен для обратной совместимости
     *             с внешними реализациями интерфейса; удаление в 1.15.0.
     */
    @Deprecated
    default void onHealOut(EntityRegainHealthEvent event, Player healer) {
    }

    /** Сброс proc-счётчиков (выход игрока, фолиант). */
    void clear(UUID uuid);
}

// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.passive;

import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.UUID;

/**
 * 1.11.4 (P1): контракт пассивки класса. Один класс = один файл-реализация;
 * PassiveListener = тонкий диспетчер без логики.
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

    /** Событие лечения с маркером хилера на игроке этого класса. */
    default void onHealOut(EntityRegainHealthEvent event, Player healer) {
    }

    /** Сброс proc-счётчиков (выход игрока, фолиант). */
    void clear(UUID uuid);
}

// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability.passive;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1.11.4 (P1): ТОНКИЙ диспетчер пассивок. Вся логика — в ClassPassive-файлах.
 * Маркер хилера статический: ставят PriestAbilities, читает ResourceService.
 * 1.14.0 (Б8): +MagePassives (mana_soaked) — ранее файл отсутствовал.
 * 1.14.1 (Волна 1): onRegainHealth применял классово-независимые исходящие
 *   heal-множители ДО диспетчеризации класс-пассивок.
 * 1.14.7 (Спринт 2, P1-1): множители heal_out_pct и роли HEALER УБРАНЫ из
 *   onRegainHealth — они применяются в единой точке Spec2RoleListener.onCustomHeal
 *   на событии CustomHealEvent (HpBarService.heal). Ванильные EntityRegainHealthEvent
 *   (зелья/еда/natural regen) больше не домножаются — это закрывает «двойной путь
 *   лечения» из аудита без потери множителей на kit-хилах.
 * 1.14.7 (Спринт 2, P1-2): peekHealerMark → pollHealerMark (сбрасывающее чтение) —
 *   закрывает окно stale-маркера, когда следующий ванильный regain подхватывал
 *   чужую метку жреца. Одна метка = ровно одно событие лечения.
 *   markHealer помечен @Deprecated — удаление в Sprint 4 после перевода всех
 *   kit-хилов на явную атрибуцию через CustomHealEvent.getHealer().
 */
public final class PassiveListener implements Listener {

    /**
     * 1.14.7 (P1-2): @Deprecated — атрибут хилера должен жить в CustomHealEvent.
     * Оставлен для совместимости с PriestAbilities.applyHealWith до Sprint 4.
     */
    @Deprecated
    private static volatile UUID healerMark = null;

    @Deprecated
    public static void markHealer(UUID priestUuid) {
        healerMark = priestUuid;
    }

    public static UUID pollHealerMark() {
        UUID v = healerMark;
        healerMark = null;
        return v;
    }

    private static UUID peekHealerMark() {
        return healerMark;
    }

    private final RaskolClasses plugin;
    private final Map<PlayerClass, ClassPassive> passives = new EnumMap<>(PlayerClass.class);

    public PassiveListener(RaskolClasses plugin) {
        this.plugin = plugin;
        for (ClassPassive p : List.of(
                new WarriorPassives(plugin),
                new HunterPassives(plugin),
                new RoguePassives(plugin),
                new PriestPassives(plugin),
                new MagePassives(plugin),
                new WarlockPassives(plugin))) {
            passives.put(p.playerClass(), p);
        }
    }

    public void clear(UUID uuid) {
        passives.values().forEach(p -> p.clear(uuid));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = BaseClassPassive.resolvePlayer(event.getDamager());
        if (attacker != null && event.getEntity() instanceof LivingEntity target) {
            ClassPassive p = passives.get(plugin.getClassProvider().getClassOf(attacker));
            if (p != null) {
                p.onDamageOut(event, attacker, target, event.getDamage());
            }
        }
        if (event.getEntity() instanceof Player victim) {
            ClassPassive pv = passives.get(plugin.getClassProvider().getClassOf(victim));
            if (pv != null) {
                pv.onDamageIn(event, victim, event.getDamage());
            }
        }
    }

    /**
     * 1.14.7 (P1-1/P1-2): обработка ванильных regain-событий.
     *
     * P1-2: читаем pollHealerMark() — сбрасывающее чтение. Одна метка = ровно одно
     * событие лечения. Раньше peekHealerMark() оставлял метку, и следующий regain
     * (например, natural regen тиком позже) подхватывал чужую метку жреца.
     *
     * P1-1: множители heal_out_pct и роли HEALER больше НЕ применяются здесь —
     * их применяет Spec2RoleListener.onCustomHeal на CustomHealEvent, которое
     * генерирует HpBarService.heal. Это закрывает «двойной путь лечения»: kit-хилы
     * жреца идут через CustomHealEvent (с множителями), ванильные regain'ы идут
     * «как есть» (без множителей — это правильно, т.к. роль/узел принадлежит
     * классу-хилеру, а не источнику регенерации).
     *
     * onHealOut классовых пассивок оставлен — счётчики/проки классов работают как
     * прежде (если пассивка что-то делает на исходящий хил).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        UUID healer = pollHealerMark();
        if (healer == null) {
            return;
        }
        Player healerPlayer = plugin.getServer().getPlayer(healer);
        if (healerPlayer == null) {
            return;
        }

        ClassPassive p = passives.get(plugin.getClassProvider().getClassOf(healerPlayer));
        if (p != null) {
            p.onHealOut(event, healerPlayer);
        }
    }
}

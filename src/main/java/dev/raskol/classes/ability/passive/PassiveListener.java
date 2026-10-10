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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1.11.4 (P1): ТОНКИЙ диспетчер пассивок. Вся логика — в ClassPassive-файлах.
 * 1.14.0 (Б8): +MagePassives (mana_soaked) — ранее файл отсутствовал.
 *
 * 1.14.7 (Sprint 4, P1-1/P1-2): ПОЛНОЕ удаление глобального состояния лечения.
 *   - static volatile healerMark, markHealer/pollHealerMark/peekHealerMark — удалены;
 *   - обработчик onRegainHealth удалён: композиция исходящего лечения (heal_out_pct,
 *     роль HEALER) живёт ТОЛЬКО в Spec2RoleListener.onCustomHeal (CustomHealEvent),
 *     входящий множитель роли (heal_received_pct, TANK) — в Spec2RoleListener.onRegain,
 *     ресурс-он-хил — в ResourceService.onCustomHeal (Sprint 2);
 *   - ClassPassive.onHealOut остаётся в интерфейсе как deprecated-пустой до волны 4.6:
 *     диспетчера у него больше нет (ванильный regain не имеет атрибуции целителя,
 *     а mark-атрибуция была источником misattribution-багов P1-2).
 *   Итог: одно лечение = один множитель, глобального mutable-состояния в слушателе нет.
 */
public final class PassiveListener implements Listener {

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
}

// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability.passive;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.spec.SpecRole;
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
 * 1.14.1 (Волна 1): onRegainHealth применяет классово-независимые исходящие
 *   heal-множители ДО диспетчеризации класс-пассивок:
 *     1) heal_out_pct из spec2-агрегата (Spec2Service.healOutPercent) — теперь
 *        работает у ВСЕХ классов (field_medkit Воина и т.д.), не только у Жреца;
 *     2) роль HEALER: множитель из spec2.role-passives.HEALER.heal-mult.
 *   «Благодать» Жреца больше не добавляет healOutPercent сама (см. PriestPassives),
 *   двойного счёта нет.
 */
public final class PassiveListener implements Listener {

    /** Маркер хилера: PriestAbilities ставит перед heal(), ResourceService читает. */
    private static volatile UUID healerMark = null;

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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        UUID healer = peekHealerMark();
        if (healer == null) {
            return;
        }
        Player healerPlayer = plugin.getServer().getPlayer(healer);
        if (healerPlayer == null) {
            return;
        }

        // 1.14.1 (Волна 1): универсальные исходящие heal-множители.
        double mult = 1.0 + plugin.getSpec2Service().healOutPercent(healer) / 100.0;
        if (plugin.getSpec2Service().roleOfOwner(healer) == SpecRole.HEALER) {
            mult *= 1.0 + plugin.getConfig().getDouble(
                    "spec2.role-passives.HEALER.heal-mult", 0.05);
        }
        if (mult != 1.0 && event.getAmount() > 0.0) {
            event.setAmount(event.getAmount() * mult);
        }

        ClassPassive p = passives.get(plugin.getClassProvider().getClassOf(healerPlayer));
        if (p != null) {
            p.onHealOut(event, healerPlayer);
        }
    }
}

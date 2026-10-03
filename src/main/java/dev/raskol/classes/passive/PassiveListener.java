// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.passive;

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
        Player priest = plugin.getServer().getPlayer(healer);
        if (priest == null) {
            return;
        }
        ClassPassive p = passives.get(plugin.getClassProvider().getClassOf(priest));
        if (p != null) {
            p.onHealOut(event, priest);
        }
    }
}

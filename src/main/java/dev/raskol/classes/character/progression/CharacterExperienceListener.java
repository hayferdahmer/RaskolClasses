// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.character.progression;

import java.util.Set;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import dev.raskol.classes.RaskolClasses;

/** Выдаёт RPG-опыт только за убийства существ, отмеченных при допустимом естественном появлении. */
public final class CharacterExperienceListener implements Listener {

    private static final Set<String> EXPERIENCE_SPAWN_REASONS = Set.of(
            "NATURAL",
            "CHUNK_GEN",
            "PATROL",
            "RAID",
            "REINFORCEMENTS",
            "VILLAGE_DEFENSE",
            "VILLAGE_INVASION");

    private final RaskolClasses plugin;
    private final NamespacedKey eligibleSpawnKey;

    public CharacterExperienceListener(RaskolClasses plugin) {
        this.plugin = plugin;
        this.eligibleSpawnKey = new NamespacedKey(plugin, "rpg_xp_eligible");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || !EXPERIENCE_SPAWN_REASONS.contains(event.getSpawnReason().name())) {
            return;
        }
        entity.getPersistentDataContainer().set(eligibleSpawnKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player
                || !Byte.valueOf((byte) 1).equals(
                        entity.getPersistentDataContainer().get(eligibleSpawnKey, PersistentDataType.BYTE))) {
            return;
        }

        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }

        long reward = plugin.getConfig().getLong("experience.mob-rewards." + entity.getType().name(), 0L);
        if (reward <= 0) {
            return;
        }

        CharacterProgressionEngine.AwardResult result =
                plugin.getCharacterExperience().award(killer.getUniqueId(), reward);
        if (result.levelsGained() > 0
                && plugin.getConfig().getBoolean("experience.notifications.level-up", true)) {
            killer.sendMessage("§6Уровень персонажа повышен: §e" + result.state().level());
        }
    }
}

// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.character.progression;

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

    private final RaskolClasses plugin;
    private final NamespacedKey eligibleSpawnKey;

    public CharacterExperienceListener(RaskolClasses plugin) {
        this.plugin = plugin;
        this.eligibleSpawnKey = new NamespacedKey(plugin, "rpg_xp_eligible");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player
                || !CharacterExperienceSpawnPolicy.isEligibleSpawn(
                        entity.getType().name(), event.getSpawnReason().name())) {
            return;
        }
        entity.getPersistentDataContainer().set(eligibleSpawnKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }

        Byte eligibleSpawn = entity.getPersistentDataContainer().get(eligibleSpawnKey, PersistentDataType.BYTE);
        long reward = plugin.getConfig()
                .getLong("experience.mob-rewards." + entity.getType().name(), 0L);
        if (!CharacterExperienceSpawnPolicy.shouldAwardExperience(
                entity instanceof Player, true, Byte.valueOf((byte) 1).equals(eligibleSpawn), reward)) {
            return;
        }

        CharacterProgressionEngine.AwardResult result =
                plugin.getCharacterExperience().award(killer.getUniqueId(), reward);
        boolean notifyLevelUp =
                result.levelsGained() > 0 && plugin.getConfig().getBoolean("experience.notifications.level-up", true);
        if (notifyLevelUp) {
            killer.sendMessage(
                    "§6Уровень персонажа повышен: §e" + result.state().level());
        }
    }
}

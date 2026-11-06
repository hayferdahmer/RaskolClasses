// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.api.combat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Удаляет профили интеграции (RaskolMobs), когда их сущности покидают мир сервера.
 *
 * 1.15.7 (Волна 7c, аудит 2.6-18): убран handler EntitiesUnloadEvent.
 * Раньше профиль удалялся при выгрузке чанка, хотя сущность могла вернуться
 * (игрок зайдёт в чанк снова) — профиль терялся преждевременно.
 * Теперь удаление только по EntityDeathEvent (окончательная смерть).
 * Priority MONITOR: даём другим плагинам (RaskolMobs resurrect) шанс отменить
 * смерть до удаления профиля.
 */
public final class CombatProfileLifecycleListener implements Listener {
    private final CombatProfileRegistry registry;

    public CombatProfileLifecycleListener(CombatProfileRegistry registry) {
        this.registry = registry;
    }

    /**
     * Смерть сущности: удаляем профиль.
     * Priority MONITOR: другие плагины могут отменить смерть (resurrect),
     * но EntityDeathEvent не cancellable в Bukkit, поэтому удаляем безусловно.
     * Для отмены смерти RaskolMobs должен сам восстановить профиль через
     * CombatProfileRegistry.register().
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void death(EntityDeathEvent event) {
        registry.unregister(event.getEntity().getUniqueId());
    }
}

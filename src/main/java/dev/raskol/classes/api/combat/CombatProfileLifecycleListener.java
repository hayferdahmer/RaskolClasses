package dev.raskol.classes.api.combat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/** Removes integration-owned profiles when their entities leave the server world. */
public final class CombatProfileLifecycleListener implements Listener {
    private final CombatProfileRegistry registry;
    public CombatProfileLifecycleListener(CombatProfileRegistry registry){this.registry=registry;}
    @EventHandler public void death(EntityDeathEvent event){registry.unregister(event.getEntity().getUniqueId());}
    @EventHandler public void unload(EntitiesUnloadEvent event){event.getEntities().forEach(entity->registry.unregister(entity.getUniqueId()));}
}

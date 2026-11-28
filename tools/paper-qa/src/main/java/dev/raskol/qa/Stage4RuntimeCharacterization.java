// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.qa;

import dev.raskol.classes.combat.CombatService;
import dev.raskol.classes.effect.ActiveEffectManager;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.RegisteredListener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Изолированная Paper 26.2 characterization-проверка Stage 4.
 *
 * <p>Проверяет фактический порядок listener registrations, порядок вызова
 * generic/specific handlers на одном listener instance и вложенность reflect marker.
 * Не изменяет production listeners и боевые формулы.</p>
 */
final class Stage4RuntimeCharacterization {

    private Stage4RuntimeCharacterization() {}

    static void run(JavaPlugin qaPlugin) {
        try {
            characterizeListenerOrder(qaPlugin);
            characterizeReflectNesting(qaPlugin);
        } catch (RuntimeException exception) {
            qaPlugin.getLogger().severe(
                    "[RASKOL-COMBAT-QA] LISTENER_ORDER_FAIL: " + exception);
        }
    }

    private static void characterizeListenerOrder(JavaPlugin qaPlugin) {
        Plugin raskol = Bukkit.getPluginManager().getPlugin("RaskolClasses");
        if (raskol == null || !raskol.isEnabled()) {
            throw new IllegalStateException("RaskolClasses is not enabled");
        }

        HandlerList genericHandlers = EntityDamageEvent.getHandlerList();
        HandlerList byEntityHandlers = EntityDamageByEntityEvent.getHandlerList();
        if (genericHandlers != byEntityHandlers) {
            throw new IllegalStateException(
                    "EntityDamageByEntityEvent does not share the EntityDamageEvent handler list");
        }

        List<String> highSequence = Arrays.stream(byEntityHandlers.getRegisteredListeners())
                .filter(registered -> registered.getPlugin() == raskol)
                .filter(registered -> registered.getPriority() == EventPriority.HIGH)
                .map(RegisteredListener::getListener)
                .map(listener -> listener.getClass().getSimpleName())
                .filter(name -> name.equals("Spec2RoleListener")
                        || name.equals("PetService")
                        || name.equals("ActiveEffectManager")
                        || name.equals("PassiveListener"))
                .toList();

        List<String> expected = List.of(
                "Spec2RoleListener",
                "PetService",
                "ActiveEffectManager",
                "ActiveEffectManager",
                "PassiveListener");
        if (!highSequence.equals(expected)) {
            throw new IllegalStateException(
                    "Unexpected RaskolClasses HIGH listener order: " + highSequence);
        }

        List<String> activeEffectMethodOrder = Arrays.stream(ActiveEffectManager.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(EventHandler.class))
                .filter(method -> method.getAnnotation(EventHandler.class).priority() == EventPriority.HIGH)
                .filter(method -> method.getParameterCount() == 1)
                .filter(method -> method.getParameterTypes()[0].isAssignableFrom(EntityDamageByEntityEvent.class))
                .map(Method::getName)
                .toList();
        if (activeEffectMethodOrder.size() != 2
                || !activeEffectMethodOrder.contains("onIncomingLate")
                || !activeEffectMethodOrder.contains("onOutgoingLate")) {
            throw new IllegalStateException(
                    "ActiveEffectManager HIGH generic/specific handler set changed: " + activeEffectMethodOrder);
        }

        List<String> observedProbeOrder = runGenericSpecificProbe(qaPlugin);
        List<String> declaredProbeOrder = Arrays.stream(GenericSpecificProbe.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(EventHandler.class))
                .filter(method -> method.getAnnotation(EventHandler.class).priority() == EventPriority.HIGH)
                .map(Method::getName)
                .map(name -> name.equals("onGeneric") ? "generic" : "specific")
                .toList();
        if (!observedProbeOrder.equals(declaredProbeOrder)) {
            throw new IllegalStateException(
                    "Paper invocation order " + observedProbeOrder
                            + " differs from the runtime method-registration order " + declaredProbeOrder);
        }

        qaPlugin.getLogger().info(
                "[RASKOL-COMBAT-QA] LISTENER_ORDER_PASS"
                        + " highSequence=" + String.join(",", highSequence)
                        + " activeEffectMethodOrder=" + String.join(",", activeEffectMethodOrder)
                        + " genericSpecificPaperOrder=" + String.join(",", observedProbeOrder));
    }

    private static List<String> runGenericSpecificProbe(JavaPlugin qaPlugin) {
        World world = Bukkit.getWorlds().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No loaded world for listener-order probe"));
        Location origin = world.getSpawnLocation().clone().add(0.0, 2.0, 0.0);
        Entity source = null;
        Entity victim = null;
        GenericSpecificProbe probe = null;
        try {
            source = spawnPassiveMob(world, origin);
            victim = spawnPassiveMob(world, origin.clone().add(2.0, 0.0, 0.0));
            if (!(victim instanceof LivingEntity livingVictim)) {
                throw new IllegalStateException("Listener-order victim is not living");
            }

            probe = new GenericSpecificProbe(livingVictim.getUniqueId());
            Bukkit.getPluginManager().registerEvents(probe, qaPlugin);
            livingVictim.setNoDamageTicks(0);
            livingVictim.damage(0.5, source);

            List<String> observed = List.copyOf(probe.calls);
            if (observed.size() != 2
                    || !observed.contains("generic")
                    || !observed.contains("specific")) {
                throw new IllegalStateException(
                        "Expected one generic and one specific handler call, got " + observed);
            }
            return observed;
        } finally {
            if (probe != null) {
                HandlerList.unregisterAll(probe);
            }
            remove(source);
            remove(victim);
        }
    }

    private static Entity spawnPassiveMob(World world, Location location) {
        Entity entity = world.spawnEntity(location, EntityType.ZOMBIE);
        if (entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setAware(false);
        }
        if (entity instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
            living.setNoDamageTicks(0);
        }
        return entity;
    }

    private static void remove(Entity entity) {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private static void characterizeReflectNesting(JavaPlugin qaPlugin) {
        boolean outerVisible;
        boolean visibleAfterInnerEnd;
        boolean clearedAfterOuterEnd;
        CombatService.beginReflect();
        try {
            outerVisible = CombatService.reflectSuppressed();
            CombatService.beginReflect();
            CombatService.endReflect();
            visibleAfterInnerEnd = CombatService.reflectSuppressed();
        } finally {
            if (CombatService.reflectSuppressed()) {
                CombatService.endReflect();
            }
        }
        clearedAfterOuterEnd = !CombatService.reflectSuppressed();

        if (!outerVisible || !clearedAfterOuterEnd) {
            qaPlugin.getLogger().severe(
                    "[RASKOL-COMBAT-QA] REFLECT_NESTING_FAIL"
                            + " outerVisible=" + outerVisible
                            + " clearedAfterOuterEnd=" + clearedAfterOuterEnd);
            return;
        }

        String state = visibleAfterInnerEnd ? "DEPTH_SAFE" : "NESTED_CLEAR_BUG";
        qaPlugin.getLogger().warning(
                "[RASKOL-COMBAT-QA] REFLECT_NESTING_CHARACTERIZED state=" + state);
    }

    private static final class GenericSpecificProbe implements Listener {
        private final UUID targetId;
        private final List<String> calls = new ArrayList<>();

        private GenericSpecificProbe(UUID targetId) {
            this.targetId = targetId;
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
        public void onGeneric(EntityDamageEvent event) {
            if (event.getEntity().getUniqueId().equals(targetId)) {
                calls.add("generic");
            }
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
        public void onSpecific(EntityDamageByEntityEvent event) {
            if (event.getEntity().getUniqueId().equals(targetId)) {
                calls.add("specific");
            }
        }
    }
}

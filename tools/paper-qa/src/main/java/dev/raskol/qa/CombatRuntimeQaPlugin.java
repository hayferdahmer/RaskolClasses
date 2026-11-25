// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.qa;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.combat.CombatService;
import java.lang.reflect.Field;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Временный CI-плагин для проверки жизненного цикла suppressed damage event на Paper.
 * Не входит в распространяемый JAR RaskolClasses.
 */
public final class CombatRuntimeQaPlugin extends JavaPlugin implements Listener {

    private NamespacedKey suppressedTargetKey;
    private NamespacedKey normalTargetKey;
    private boolean suppressedObserved;
    private boolean suppressedCancelled;
    private boolean normalObserved;
    private boolean normalWasSuppressed;

    private enum PathAScenario {
        REDUCE_OUTER,
        INCREASE_OUTER,
        NESTED_PASS_OUTER_CANCEL,
        NESTED_AND_OUTER_PASS_MODIFIED
    }

    private Player pathATarget;
    private LivingEntity pathASource;
    private CombatService combat;
    private UUID pathATargetId;
    private PathAScenario pathAScenario;
    private EntityDamageEvent outerEvent;
    private EntityDamageEvent nestedEvent;
    private boolean nestedDispatch;
    private boolean failed;
    private double outerInitialDamage;
    private double outerFinalDamage;
    private double nestedFinalDamage;
    private boolean outerCancelled;
    private boolean nestedCancelled;
    private double originalBurstWindowPct;
    private double originalSingleHitPct;
    private boolean configOverridden;

    @Override
    public void onEnable() {
        suppressedTargetKey = new NamespacedKey(this, "suppressed-target");
        normalTargetKey = new NamespacedKey(this, "normal-target");
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, this::runSuppressionProbe, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (target.getPersistentDataContainer().has(suppressedTargetKey, PersistentDataType.BYTE)) {
            suppressedObserved = CombatService.isSuppressed();
            suppressedCancelled = event.isCancelled();
        } else if (target.getPersistentDataContainer().has(normalTargetKey, PersistentDataType.BYTE)) {
            normalObserved = true;
            normalWasSuppressed = CombatService.isSuppressed();
        }
    }

    private void runSuppressionProbe() {
        World world = Bukkit.getWorlds().stream().findFirst().orElse(null);
        if (world == null) {
            fail("No loaded world available for the suppression probe");
            return;
        }

        Location origin = world.getSpawnLocation().clone().add(0.0, 2.0, 0.0);
        Entity suppressedSource = null;
        Entity suppressedTarget = null;
        Entity normalSource = null;
        Entity normalTarget = null;
        try {
            suppressedSource = spawnPassiveMob(world, origin.clone().add(0.0, 0.0, 0.0));
            suppressedTarget = spawnPassiveMob(world, origin.clone().add(2.0, 0.0, 0.0));
            normalSource = spawnPassiveMob(world, origin.clone().add(4.0, 0.0, 0.0));
            normalTarget = spawnPassiveMob(world, origin.clone().add(6.0, 0.0, 0.0));

            LivingEntity suppressedVictim = (LivingEntity) suppressedTarget;
            LivingEntity normalVictim = (LivingEntity) normalTarget;
            suppressedVictim.getPersistentDataContainer().set(suppressedTargetKey, PersistentDataType.BYTE, (byte) 1);
            normalVictim.getPersistentDataContainer().set(normalTargetKey, PersistentDataType.BYTE, (byte) 1);

            suppressedVictim.setNoDamageTicks(0);
            CombatService.beginSuppress();
            try {
                suppressedVictim.damage(1.0, (Entity) suppressedSource);
            } finally {
                CombatService.endSuppress();
            }

            normalVictim.setNoDamageTicks(0);
            normalVictim.damage(1.0, (Entity) normalSource);

            if (!suppressedObserved) {
                fail("Suppression marker was not visible at MONITOR after the Raskol NORMAL and HIGHEST listeners");
                return;
            }
            if (suppressedCancelled) {
                fail("Suppressed damage event was unexpectedly cancelled");
                return;
            }
            if (!normalObserved || normalWasSuppressed) {
                fail("Suppression context leaked into an ordinary Paper damage event");
                return;
            }
            getLogger().info("[RASKOL-COMBAT-QA] SUPPRESS_SCOPE_PASS");
        } catch (RuntimeException exception) {
            getLogger().severe("[RASKOL-COMBAT-QA] SUPPRESS_SCOPE_FAIL: " + exception.getMessage());
            throw exception;
        } finally {
            remove(suppressedSource);
            remove(suppressedTarget);
            remove(normalSource);
            remove(normalTarget);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (pathATarget != null) {
            return;
        }
        pathATarget = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, this::startPathAMatrix, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void mutatePathAEvent(EntityDamageEvent event) {
        if (pathATargetId == null || !pathATargetId.equals(event.getEntity().getUniqueId())
                || pathAScenario == null) {
            return;
        }

        if (nestedDispatch) {
            nestedDispatch = false;
            nestedEvent = event;
            return;
        }

        if (outerEvent != null) {
            return;
        }

        outerEvent = event;
        outerInitialDamage = event.getDamage();

        if (pathAScenario == PathAScenario.NESTED_PASS_OUTER_CANCEL
                || pathAScenario == PathAScenario.NESTED_AND_OUTER_PASS_MODIFIED) {
            nestedDispatch = true;
            pathATarget.setNoDamageTicks(0);
            try {
                pathATarget.damage(3.0, pathASource);
            } finally {
                nestedDispatch = false;
            }
        }

        switch (pathAScenario) {
            case REDUCE_OUTER, NESTED_AND_OUTER_PASS_MODIFIED -> event.setDamage(event.getDamage() * 0.5);
            case INCREASE_OUTER -> event.setDamage(event.getDamage() * 2.0);
            case NESTED_PASS_OUTER_CANCEL -> event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void capturePathASettlement(EntityDamageEvent event) {
        if (event == outerEvent) {
            outerFinalDamage = event.getDamage();
            outerCancelled = event.isCancelled();
        } else if (event == nestedEvent) {
            nestedFinalDamage = event.getDamage();
            nestedCancelled = event.isCancelled();
        }
    }

    private void startPathAMatrix() {
        try {
            RaskolClasses raskol = (RaskolClasses) Bukkit.getPluginManager().getPlugin("RaskolClasses");
            if (raskol == null) {
                failPathA("RaskolClasses plugin instance was not found");
                return;
            }
            Field combatField = RaskolClasses.class.getDeclaredField("combat");
            combatField.setAccessible(true);
            combat = (CombatService) combatField.get(raskol);
            if (combat == null) {
                failPathA("CombatService was not initialized");
                return;
            }

            originalBurstWindowPct = raskol.getConfig().getDouble("combat.burst-window-pct", 18.0);
            originalSingleHitPct = raskol.getConfig().getDouble("combat.max-single-hit-pct", 35.0);
            raskol.getConfig().set("combat.burst-window-pct", 1000.0);
            raskol.getConfig().set("combat.max-single-hit-pct", 1000.0);
            configOverridden = true;

            pathATarget.setGameMode(GameMode.SURVIVAL);
            pathATarget.setNoDamageTicks(0);
            pathATargetId = pathATarget.getUniqueId();

            World world = pathATarget.getWorld();
            pathASource = (LivingEntity) world.spawnEntity(
                    pathATarget.getLocation().clone().add(3.0, 0.0, 0.0), EntityType.ZOMBIE);
            if (pathASource instanceof Mob mob) {
                mob.setAI(false);
                mob.setAware(false);
            }
            pathASource.setRemoveWhenFarAway(false);

            runPathAScenario(PathAScenario.REDUCE_OUTER);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            failPathA("Runtime setup failed: " + exception);
        }
    }

    private void runPathAScenario(PathAScenario scenario) {
        if (failed) {
            cleanupPathA();
            return;
        }

        pathAScenario = scenario;
        outerEvent = null;
        nestedEvent = null;
        nestedDispatch = false;
        outerInitialDamage = 0.0;
        outerFinalDamage = 0.0;
        nestedFinalDamage = 0.0;
        outerCancelled = false;
        nestedCancelled = false;

        pathATarget.setHealth(pathATarget.getMaxHealth());
        pathATarget.setNoDamageTicks(0);
        combat.purgeBurstLog();

        double scale = combat.scaleOf(pathATarget);
        pathATarget.damage(10.0, pathASource);

        try {
            double reserved = reservationTotal(pathATargetId);
            double expected = switch (scenario) {
                case REDUCE_OUTER, INCREASE_OUTER -> outerFinalDamage / scale;
                case NESTED_PASS_OUTER_CANCEL -> nestedFinalDamage / scale;
                case NESTED_AND_OUTER_PASS_MODIFIED -> (nestedFinalDamage + outerFinalDamage) / scale;
            };

            requirePathA(outerEvent != null, scenario + ": outer event was not observed");
            requirePathA(close(reserved, expected),
                    scenario + ": ledger=" + reserved + " expected=" + expected
                            + " outerInitial=" + outerInitialDamage
                            + " outerFinal=" + outerFinalDamage
                            + " nestedFinal=" + nestedFinalDamage);
            switch (scenario) {
                case REDUCE_OUTER -> requirePathA(
                        !outerCancelled && outerFinalDamage < outerInitialDamage,
                        "decreased outer damage must be accepted and settled");
                case INCREASE_OUTER -> requirePathA(
                        !outerCancelled && outerFinalDamage > outerInitialDamage,
                        "increased outer damage must be accepted and settled");
                case NESTED_PASS_OUTER_CANCEL -> requirePathA(
                        nestedEvent != null && !nestedCancelled && outerCancelled,
                        "nested event must pass while outer event is cancelled");
                case NESTED_AND_OUTER_PASS_MODIFIED -> requirePathA(
                        nestedEvent != null && !nestedCancelled && !outerCancelled
                                && outerFinalDamage < outerInitialDamage,
                        "nested and modified outer events must both pass");
            }
            if (failed) {
                cleanupPathA();
                return;
            }

            getLogger().info("[RASKOL-COMBAT-QA] PATH_A_SETTLEMENT_SCENARIO_PASS scenario=" + scenario
                    + " outerInitial=" + outerInitialDamage
                    + " outerFinal=" + outerFinalDamage
                    + " nestedFinal=" + nestedFinalDamage
                    + " outerCancelled=" + outerCancelled
                    + " nestedCancelled=" + nestedCancelled
                    + " ledger=" + reserved
                    + " expected=" + expected);

            PathAScenario[] scenarios = PathAScenario.values();
            int next = scenario.ordinal() + 1;
            if (next < scenarios.length) {
                Bukkit.getScheduler().runTaskLater(this, () -> runPathAScenario(scenarios[next]), 80L);
            } else {
                getLogger().info("[RASKOL-COMBAT-QA] PATH_A_SETTLEMENT_MATRIX_PASS");
                cleanupPathA();
            }
        } catch (ReflectiveOperationException exception) {
            failPathA("Could not inspect burst ledger: " + exception);
        }
    }

    private double reservationTotal(UUID playerId) throws ReflectiveOperationException {
        Field ledgersField = combat.caps().getClass().getDeclaredField("burstLedgers");
        ledgersField.setAccessible(true);
        Map<?, ?> ledgers = (Map<?, ?>) ledgersField.get(combat.caps());
        Object ledger = ledgers.get(playerId);
        if (ledger == null) {
            return 0.0;
        }
        Field entriesField = ledger.getClass().getDeclaredField("entries");
        entriesField.setAccessible(true);
        Deque<?> entries = (Deque<?>) entriesField.get(ledger);
        double total = 0.0;
        for (Object token : entries) {
            Field amountField = token.getClass().getDeclaredField("amount");
            amountField.setAccessible(true);
            total += amountField.getDouble(token);
        }
        return total;
    }

    private boolean close(double actual, double expected) {
        return Math.abs(actual - expected) <= 0.001;
    }

    private void requirePathA(boolean condition, String message) {
        if (!condition) {
            failPathA("Assertion failed: " + message);
        }
    }

    private void failPathA(String message) {
        failed = true;
        getLogger().severe("[RASKOL-COMBAT-QA] PATH_A_SETTLEMENT_MATRIX_FAIL: " + message);
        cleanupPathA();
    }

    private void cleanupPathA() {
        if (configOverridden) {
            RaskolClasses raskol = (RaskolClasses) Bukkit.getPluginManager().getPlugin("RaskolClasses");
            if (raskol != null) {
                raskol.getConfig().set("combat.burst-window-pct", originalBurstWindowPct);
                raskol.getConfig().set("combat.max-single-hit-pct", originalSingleHitPct);
            }
            configOverridden = false;
        }
        if (pathASource != null && pathASource.isValid()) {
            pathASource.remove();
        }
        if (pathATarget != null && pathATarget.isOnline()) {
            pathATarget.kickPlayer("RASKOL Paper Path A runtime QA complete");
        }
    }

    private Entity spawnPassiveMob(World world, Location location) {
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

    private void remove(Entity entity) {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private void fail(String message) {
        getLogger().severe("[RASKOL-COMBAT-QA] SUPPRESS_SCOPE_FAIL: " + message);
    }
}

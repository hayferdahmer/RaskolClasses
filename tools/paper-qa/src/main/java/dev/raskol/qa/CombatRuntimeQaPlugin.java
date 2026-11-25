// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.qa;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.combat.CombatService;
import dev.raskol.classes.combat.DamageProfile;
import java.lang.reflect.Field;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
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
 * Временный CI-плагин: проверяет транзакции burst-резерва на настоящем Paper Player.
 * Не входит в распространяемый JAR RaskolClasses.
 */
public final class CombatRuntimeQaPlugin extends JavaPlugin implements Listener {

    private enum Scenario {
        CANCEL_PHYSICAL,
        CANCEL_MAGIC,
        CANCEL_BOTH,
        NESTED_HIT
    }

    private CombatService combat;
    private Player target;
    private LivingEntity source;
    private Scenario activeScenario;
    private UUID targetId;
    private int physicalEvents;
    private int magicEvents;
    private int cancelledPhysical;
    private int cancelledMagic;
    private boolean nestedTriggered;
    private boolean failed;
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
    private UUID pathATargetId;
    private PathAScenario pathAScenario;
    private EntityDamageEvent outerEvent;
    private EntityDamageEvent nestedEvent;
    private boolean nestedDispatch;
    private double outerInitialDamage;
    private double outerFinalDamage;
    private double nestedFinalDamage;
    private boolean outerCancelled;
    private boolean nestedCancelled;
    private double originalBurstWindowPct;
    private double originalSingleHitPct;
    private boolean configOverridden;
    private boolean cleanupDone;

    @Override
    public void onEnable() {
        suppressedTargetKey = new NamespacedKey(this, "suppressed-target");
        normalTargetKey = new NamespacedKey(this, "normal-target");
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, this::runSuppressionProbe, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observeSuppression(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (victim.getPersistentDataContainer().has(suppressedTargetKey, PersistentDataType.BYTE)) {
            suppressedObserved = CombatService.isSuppressed();
            suppressedCancelled = event.isCancelled();
        } else if (victim.getPersistentDataContainer().has(normalTargetKey, PersistentDataType.BYTE)) {
            normalObserved = true;
            normalWasSuppressed = CombatService.isSuppressed();
        }
    }

    private void runSuppressionProbe() {
        World world = Bukkit.getWorlds().stream().findFirst().orElse(null);
        if (world == null) {
            failSuppression("No loaded world available for the suppression probe");
            return;
        }

        Location origin = world.getSpawnLocation().clone().add(0.0, 2.0, 0.0);
        Entity suppressedSource = null;
        Entity suppressedTarget = null;
        Entity normalSource = null;
        Entity normalTarget = null;
        try {
            suppressedSource = spawnPassiveMob(world, origin.clone());
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
                suppressedVictim.damage(1.0, suppressedSource);
            } finally {
                CombatService.endSuppress();
            }

            normalVictim.setNoDamageTicks(0);
            normalVictim.damage(1.0, normalSource);

            if (!suppressedObserved) {
                failSuppression("Suppression marker was not visible at MONITOR after NORMAL and HIGHEST listeners");
                return;
            }
            if (suppressedCancelled) {
                failSuppression("Suppressed damage event was unexpectedly cancelled");
                return;
            }
            if (!normalObserved || normalWasSuppressed) {
                failSuppression("Suppression context leaked into an ordinary Paper damage event");
                return;
            }
            getLogger().info("[RASKOL-COMBAT-QA] SUPPRESS_SCOPE_PASS");
        } catch (RuntimeException exception) {
            failSuppression("Paper suppression probe threw: " + exception);
        } finally {
            remove(suppressedSource);
            remove(suppressedTarget);
            remove(normalSource);
            remove(normalTarget);
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

    private void failSuppression(String message) {
        failed = true;
        getLogger().severe("[RASKOL-COMBAT-QA] SUPPRESS_SCOPE_FAIL: " + message);
        cleanup();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (target != null) {
            return;
        }
        target = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, this::startMatrix, 100L);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void interceptCombatChannel(EntityDamageEvent event) {
        if (targetId == null || !targetId.equals(event.getEntity().getUniqueId())) {
            return;
        }
        getLogger().info("[RASKOL-COMBAT-QA] DAMAGE_EVENT scenario=" + activeScenario
                + " cause=" + event.getCause()
                + " suppressed=" + CombatService.isSuppressed()
                + " cancelledAtLowest=" + event.isCancelled()
                + " damage=" + event.getDamage());

        if (!CombatService.isSuppressed()) {
            return;
        }

        boolean magic = event.getCause() == EntityDamageEvent.DamageCause.MAGIC;
        boolean physical = event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK;
        if (!magic && !physical) {
            fail("Unexpected synthetic damage cause: " + event.getCause());
            return;
        }

        if (physical) {
            physicalEvents++;
        } else {
            magicEvents++;
        }

        boolean cancel = switch (activeScenario) {
            case CANCEL_PHYSICAL -> physical;
            case CANCEL_MAGIC -> magic;
            case CANCEL_BOTH -> true;
            case NESTED_HIT -> physical && !nestedTriggered;
        };

        if (activeScenario == Scenario.NESTED_HIT && physical && !nestedTriggered) {
            nestedTriggered = true;
            combat.dealDamage(target, source, DamageProfile.hybrid(1.0, 1.0));
        }

        if (cancel) {
            event.setCancelled(true);
            if (physical) {
                cancelledPhysical++;
            } else {
                cancelledMagic++;
            }
        }
    }

    private void startMatrix() {
        if (failed) {
            return;
        }
        try {
            RaskolClasses raskol = (RaskolClasses) Bukkit.getPluginManager().getPlugin("RaskolClasses");
            if (raskol == null) {
                fail("RaskolClasses plugin instance was not found");
                return;
            }
            Field combatField = RaskolClasses.class.getDeclaredField("combat");
            combatField.setAccessible(true);
            combat = (CombatService) combatField.get(raskol);
            if (combat == null) {
                fail("CombatService was not initialized");
                return;
            }

            // Поднимаем только тестовые лимиты, чтобы сценарии проверяли rollback,
            // а не упирались в намеренно малый burst cap.
            originalBurstWindowPct = raskol.getConfig().getDouble("combat.burst-window-pct", 18.0);
            originalSingleHitPct = raskol.getConfig().getDouble("combat.max-single-hit-pct", 35.0);
            raskol.getConfig().set("combat.burst-window-pct", 1000.0);
            raskol.getConfig().set("combat.max-single-hit-pct", 1000.0);
            configOverridden = true;

            target.setGameMode(GameMode.SURVIVAL);
            target.setNoDamageTicks(0);
            targetId = target.getUniqueId();

            World world = target.getWorld();
            Location spawn = target.getLocation().clone().add(3.0, 0.0, 0.0);
            Entity mob = world.spawnEntity(spawn, EntityType.ZOMBIE);
            source = (LivingEntity) mob;
            if (source instanceof Mob passive) {
                passive.setAI(false);
                passive.setAware(false);
            }
            source.setRemoveWhenFarAway(false);

            runScenario(Scenario.CANCEL_PHYSICAL, 0);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            fail("Runtime matrix setup failed: " + exception);
        }
    }

    private void runScenario(Scenario scenario, int index) {
        if (failed) {
            cleanup();
            return;
        }
        if (index > 0) {
            Bukkit.getScheduler().runTaskLater(this, () -> runScenario(scenario, index), 80L);
            return;
        }

        activeScenario = scenario;
        physicalEvents = 0;
        magicEvents = 0;
        cancelledPhysical = 0;
        cancelledMagic = 0;
        nestedTriggered = false;

        target.setHealth(target.getMaxHealth());
        target.setNoDamageTicks(0);
        combat.purgeBurstLog();

        double before = target.getHealth();
        DamageProfile probeProfile = DamageProfile.hybrid(2.0, 2.0);
        getLogger().info("[RASKOL-COMBAT-QA] BURST_PREFLIGHT"
                + " targetValid=" + target.isValid()
                + " targetDead=" + target.isDead()
                + " targetInvulnerable=" + target.isInvulnerable()
                + " gameMode=" + target.getGameMode()
                + " sourceValid=" + (source != null && source.isValid())
                + " canHit=" + combat.canHit(source, target)
                + " simulatedTaken=" + combat.simulateTaken(target, probeProfile)
                + " formulaMax=" + combat.formulaMaxOf(target)
                + " scale=" + combat.scaleOf(target)
                + " health=" + before);
        double reportedDamage = combat.dealDamage(target, source, probeProfile);
        double actualDamage = before - target.getHealth();
        getLogger().info("[RASKOL-COMBAT-QA] BURST_RESULT scenario=" + scenario
                + " reportedDamage=" + reportedDamage
                + " actualHealthDelta=" + actualDamage
                + " healthAfter=" + target.getHealth());
        final double reserved;
        try {
            reserved = reservationTotal(targetId);
        } catch (ReflectiveOperationException exception) {
            fail("Could not inspect the burst reservation ledger: " + exception);
            return;
        }

        switch (scenario) {
            case CANCEL_PHYSICAL -> {
                require(physicalEvents == 1 && magicEvents == 1,
                        "physical-cancel event counts: physical=" + physicalEvents + " magic=" + magicEvents
                                + " actualDamage=" + actualDamage + " reserved=" + reserved);
                require(cancelledPhysical == 1 && cancelledMagic == 0,
                        "physical-only cancellation: cancelledPhysical=" + cancelledPhysical
                                + " cancelledMagic=" + cancelledMagic);
                require(actualDamage > 0.001,
                        "magic channel must apply after physical cancellation: actualDamage=" + actualDamage);
                require(close(reserved, actualDamage / combat.scaleOf(target)), "reservation must equal actual applied magic damage; got " + reserved);
            }
            case CANCEL_MAGIC -> {
                require(physicalEvents == 1 && magicEvents == 1, "magic-cancel event counts");
                require(cancelledPhysical == 0 && cancelledMagic == 1, "magic-only cancellation");
                require(actualDamage > 0.001, "physical channel must apply after magic cancellation");
                require(close(reserved, actualDamage / combat.scaleOf(target)), "reservation must equal actual applied physical damage; got " + reserved);
            }
            case CANCEL_BOTH -> {
                require(physicalEvents == 1 && magicEvents == 1, "both-cancel event counts");
                require(cancelledPhysical == 1 && cancelledMagic == 1, "both channels must be cancelled");
                require(Math.abs(actualDamage) < 0.001, "cancelled channels must not change health");
                require(close(reserved, 0.0), "fully cancelled hit must release its complete reservation; got " + reserved);
            }
            case NESTED_HIT -> {
                require(physicalEvents == 2 && magicEvents == 2, "nested event counts");
                require(cancelledPhysical == 1 && cancelledMagic == 0, "only the outer physical channel must be cancelled");
                require(actualDamage > 0.001, "nested and outer magic channels must apply");
                require(close(reserved, actualDamage / combat.scaleOf(target)), "nested and outer reservations must equal their actual combined health delta; got " + reserved);
            }
        }

        if (failed) {
            cleanup();
            return;
        }

        getLogger().info("[RASKOL-COMBAT-QA] BURST_SCENARIO_PASS scenario=" + scenario
                + " physicalEvents=" + physicalEvents
                + " magicEvents=" + magicEvents
                + " cancelledPhysical=" + cancelledPhysical
                + " cancelledMagic=" + cancelledMagic
                + " actualHealthDelta=" + actualDamage
                + " reserved=" + reserved);

        if (scenario == Scenario.CANCEL_PHYSICAL) {
            Bukkit.getScheduler().runTaskLater(this, () -> runScenario(Scenario.CANCEL_MAGIC, 0), 80L);
        } else if (scenario == Scenario.CANCEL_MAGIC) {
            Bukkit.getScheduler().runTaskLater(this, () -> runScenario(Scenario.CANCEL_BOTH, 0), 80L);
        } else if (scenario == Scenario.CANCEL_BOTH) {
            Bukkit.getScheduler().runTaskLater(this, () -> runScenario(Scenario.NESTED_HIT, 0), 80L);
        } else {
            getLogger().info("[RASKOL-COMBAT-QA] BURST_CANCELLATION_MATRIX_PASS");
            Bukkit.getScheduler().runTaskLater(this, this::startPathAMatrix, 20L);
        }
    }

    private void startPathAMatrix() {
        if (failed) {
            cleanup();
            return;
        }
        pathATarget = target;
        pathATargetId = targetId;
        pathATarget.setGameMode(GameMode.SURVIVAL);
        pathATarget.setNoDamageTicks(0);
        getLogger().info("[RASKOL-COMBAT-QA] PATH_A_SETTLEMENT_MATRIX_START");
        runPathAScenario(PathAScenario.REDUCE_OUTER);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void mutatePathAEvent(EntityDamageEvent event) {
        if (pathATarget != null && pathATarget.getUniqueId().equals(event.getEntity().getUniqueId())) {
            getLogger().info("[RASKOL-COMBAT-QA] PATH_A_EVENT_RECEIVED scenario=" + pathAScenario
                    + " cause=" + event.getCause()
                    + " damage=" + event.getDamage()
                    + " cancelled=" + event.isCancelled());
        }
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
                dispatchPathAEvent(2.0);
            } finally {
                nestedDispatch = false;
                pathATarget.setNoDamageTicks(0);
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

    private void runPathAScenario(PathAScenario scenario) {
        if (failed) {
            cleanup();
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
        getLogger().info("[RASKOL-COMBAT-QA] PATH_A_SCENARIO_START scenario=" + scenario
                + " player=" + pathATarget.getUniqueId()
                + " dispatch=PaperPluginManager");
        dispatchPathAEvent(4.0);
        getLogger().info("[RASKOL-COMBAT-QA] PATH_A_SCENARIO_DISPATCHED scenario=" + scenario
                + " eventObserved=" + (outerEvent != null));

        try {
            double reserved = reservationTotal(pathATargetId);
            double expected = switch (scenario) {
                case REDUCE_OUTER, INCREASE_OUTER -> outerFinalDamage / scale;
                case NESTED_PASS_OUTER_CANCEL -> nestedFinalDamage / scale;
                case NESTED_AND_OUTER_PASS_MODIFIED -> (nestedFinalDamage + outerFinalDamage) / scale;
            };

            requirePathA(outerEvent != null, scenario + ": outer event was not observed");
            requirePathA(closePathA(reserved, expected),
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
                cleanup();
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
                cleanup();
            }
        } catch (ReflectiveOperationException exception) {
            failPathA("Could not inspect burst ledger: " + exception);
        }
    }

    private void dispatchPathAEvent(double damage) {
        DamageSource source = DamageSource.builder(DamageType.GENERIC).build();
        EntityDamageEvent event = new EntityDamageEvent(
                pathATarget,
                EntityDamageEvent.DamageCause.CUSTOM,
                source,
                damage);
        Bukkit.getPluginManager().callEvent(event);
    }

    private boolean closePathA(double actual, double expected) {
        return Math.abs(actual - expected) <= 0.001;
    }

    private void requirePathA(boolean condition, String message) {
        if (!condition) {
            failPathA("Assertion failed: " + message);
        }
    }

    private void failPathA(String message) {
        if (failed) {
            return;
        }
        failed = true;
        getLogger().severe("[RASKOL-COMBAT-QA] PATH_A_SETTLEMENT_MATRIX_FAIL: " + message);
        cleanup();
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
        return Math.abs(actual - expected) < 0.05;
    }

    private void require(boolean condition, String label) {
        if (!condition) {
            fail("Assertion failed: " + label);
        }
    }

    private void fail(String message) {
        failed = true;
        getLogger().severe("[RASKOL-COMBAT-QA] BURST_CANCELLATION_MATRIX_FAIL: " + message);
        cleanup();
    }

    private void cleanup() {
        if (cleanupDone) {
            return;
        }
        cleanupDone = true;
        if (configOverridden) {
            RaskolClasses raskol = (RaskolClasses) Bukkit.getPluginManager().getPlugin("RaskolClasses");
            if (raskol != null) {
                raskol.getConfig().set("combat.burst-window-pct", originalBurstWindowPct);
                raskol.getConfig().set("combat.max-single-hit-pct", originalSingleHitPct);
            }
            configOverridden = false;
        }
        if (source != null && source.isValid()) {
            source.remove();
        }
        if (target != null && target.isOnline()) {
            target.kickPlayer("RASKOL Paper combined combat runtime QA complete");
        }
    }
}

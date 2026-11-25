// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.qa;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.combat.CombatService;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.classsystem.PlayerClass;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
    private RaskolClasses raskolPlugin;
    private LivingEntity blackMassTarget;
    private UUID blackMassTargetId;
    private boolean blackMassNestedTriggered;
    private int blackMassEvents;
    private int blackMassAttributedEvents;
    private double blackMassExpectedHeal;
    private double blackMassHealthBefore;
    private double blackMassLifestealFraction;
    private double previousWarlockRecoil;
    private boolean previousBlackMassEnabled;
    private ConfigurationSection warlockConfigSection;
    private boolean hadWarlockRecoilSetting;
    private boolean hadBlackMassEnabledSetting;
    private Object previousClassCacheEntry;
    private Map<UUID, Object> classCacheEntries;
    private boolean blackMassContextPrepared;
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
    public void triggerNestedBlackMassHit(EntityDamageEvent event) {
        if (blackMassTargetId == null
                || !blackMassTargetId.equals(event.getEntity().getUniqueId())
                || !(event instanceof EntityDamageByEntityEvent byEntity)
                || !byEntity.getDamager().getUniqueId().equals(target.getUniqueId())
                || !CombatService.isSuppressed()) {
            return;
        }

        if (!target.getUniqueId().equals(CombatService.abilitySourceMark())) {
            failBlackMass("outer or nested damage event did not carry the Warlock source UUID at LOWEST");
            return;
        }

        if (!blackMassNestedTriggered) {
            blackMassNestedTriggered = true;
            combat.dealDamage(blackMassTarget, target, new DamageProfile(1.0, 0.0, 0.0));
            if (!target.getUniqueId().equals(CombatService.abilitySourceMark())) {
                failBlackMass("outer ability source UUID was not restored after the nested dealDamage call");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void observeBlackMassLifesteal(EntityDamageEvent event) {
        if (blackMassTargetId == null
                || !blackMassTargetId.equals(event.getEntity().getUniqueId())
                || !(event instanceof EntityDamageByEntityEvent byEntity)
                || !byEntity.getDamager().getUniqueId().equals(target.getUniqueId())) {
            return;
        }

        blackMassEvents++;
        if (target.getUniqueId().equals(CombatService.abilitySourceMark())) {
            blackMassAttributedEvents++;
            blackMassExpectedHeal += event.getDamage() * blackMassLifestealFraction;
        }
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
            raskolPlugin = raskol;
            Field combatField = RaskolClasses.class.getDeclaredField("combat");
            combatField.setAccessible(true);
            combat = (CombatService) combatField.get(raskol);
            if (combat == null) {
                fail("CombatService was not initialized");
                return;
            }

            // Поднимаем только тестовые лимиты, чтобы сценарии проверяли rollback,
            // а не упирались в намеренно малый burst cap.
            raskol.getConfig().set("combat.burst-window-pct", 1000.0);
            raskol.getConfig().set("combat.max-single-hit-pct", 1000.0);
            raskol.saveConfig();

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
            runBlackMassNestedContextProbe();
        }
    }

    private void runBlackMassNestedContextProbe() {
        try {
            if (raskolPlugin == null || target == null || !target.isOnline()) {
                failBlackMass("Warlock integration probe has no online test player or plugin instance");
                return;
            }

            prepareWarlockClassCache(target);
            warlockConfigSection = raskolPlugin.getRaskolConfig().kitLoader().sectionOf(PlayerClass.WARLOCK);
            if (warlockConfigSection == null) {
                throw new IllegalStateException("Warlock kit configuration section is unavailable");
            }
            hadWarlockRecoilSetting = warlockConfigSection.contains("recoil.percent");
            hadBlackMassEnabledSetting = warlockConfigSection.contains("passives.black_mass.enabled");
            previousWarlockRecoil = raskolPlugin.getRaskolConfig().warlockRecoilPercent();
            previousBlackMassEnabled = raskolPlugin.getRaskolConfig().passiveEnabled(PlayerClass.WARLOCK, "black_mass");
            blackMassLifestealFraction = raskolPlugin.getRaskolConfig().passiveDouble(
                    PlayerClass.WARLOCK, "black_mass", "lifesteal", 0.0666);
            warlockConfigSection.set("recoil.percent", 0.0);
            warlockConfigSection.set("passives.black_mass.enabled", true);
            raskolPlugin.getRaskolConfig().kitCache().invalidate();

            World world = target.getWorld();
            Location location = target.getLocation().clone().add(3.0, 0.0, 0.0);
            Entity spawned = spawnPassiveMob(world, location);
            if (!(spawned instanceof LivingEntity living)) {
                failBlackMass("Could not spawn a LivingEntity for the Warlock lifesteal probe");
                return;
            }
            blackMassTarget = living;
            blackMassTargetId = living.getUniqueId();
            blackMassTarget.setNoDamageTicks(0);

            target.setGameMode(GameMode.SURVIVAL);
            target.setHealth(Math.max(1.0, target.getMaxHealth() - 5.0));
            blackMassHealthBefore = target.getHealth();
            blackMassNestedTriggered = false;
            blackMassEvents = 0;
            blackMassAttributedEvents = 0;
            blackMassExpectedHeal = 0.0;

            double reported = combat.dealDamage(blackMassTarget, target, new DamageProfile(2.0, 0.0, 0.0));
            double actualHeal = target.getHealth() - blackMassHealthBefore;

            requireBlackMass(blackMassNestedTriggered, "nested damage callback was not triggered");
            requireBlackMass(blackMassEvents == 2,
                    "expected exactly two real EntityDamageByEntityEvent instances, got " + blackMassEvents);
            requireBlackMass(blackMassAttributedEvents == 2,
                    "both nested and outer events must retain the Warlock source UUID; attributed="
                            + blackMassAttributedEvents);
            requireBlackMass(blackMassExpectedHeal > 0.001,
                    "expected lifesteal calculated from MONITOR event damage was not positive");
            requireBlackMass(actualHeal > 0.001,
                    "WarlockPassives did not heal the player; actualHeal=" + actualHeal);
            requireBlackMass(close(actualHeal, blackMassExpectedHeal),
                    "actual lifesteal does not match the two event contributions: actual="
                            + actualHeal + " expected=" + blackMassExpectedHeal);
            requireBlackMass(reported > 0.001, "CombatService reported no applied damage");

            if (failed) {
                cleanup();
                return;
            }

            getLogger().info("[RASKOL-COMBAT-QA] BLACK_MASS_NESTED_CONTEXT_PASS"
                    + " events=" + blackMassEvents
                    + " attributedEvents=" + blackMassAttributedEvents
                    + " expectedHeal=" + blackMassExpectedHeal
                    + " actualHeal=" + actualHeal
                    + " damageReported=" + reported);
            cleanup();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            failBlackMass("Warlock integration probe threw: " + exception);
        }
    }

    private void prepareWarlockClassCache(Player player) throws ReflectiveOperationException {
        Field cacheField = raskolPlugin.getClassProvider().getClass().getDeclaredField("cache");
        cacheField.setAccessible(true);
        Object cache = cacheField.get(raskolPlugin.getClassProvider());
        Field entriesField = cache.getClass().getDeclaredField("entries");
        entriesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> entries = (Map<UUID, Object>) entriesField.get(cache);
        classCacheEntries = entries;
        previousClassCacheEntry = entries.get(player.getUniqueId());

        Class<?> entryType = Class.forName("dev.raskol.classes.classsystem.ClassCache$Entry");
        Constructor<?> constructor = entryType.getDeclaredConstructor(PlayerClass.class, long.class);
        constructor.setAccessible(true);
        Object warlockEntry = constructor.newInstance(PlayerClass.WARLOCK, System.currentTimeMillis() + 60_000L);
        entries.put(player.getUniqueId(), warlockEntry);
        blackMassContextPrepared = true;

        if (raskolPlugin.getClassProvider().getClassOf(player) != PlayerClass.WARLOCK) {
            throw new IllegalStateException("Test class cache did not resolve the player as WARLOCK");
        }
    }

    private void requireBlackMass(boolean condition, String label) {
        if (!condition) {
            failBlackMass("Assertion failed: " + label);
        }
    }

    private void failBlackMass(String message) {
        failed = true;
        getLogger().severe("[RASKOL-COMBAT-QA] BLACK_MASS_NESTED_CONTEXT_FAIL: " + message);
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
        if (blackMassContextPrepared) {
            if (previousClassCacheEntry == null) {
                classCacheEntries.remove(target.getUniqueId());
            } else {
                classCacheEntries.put(target.getUniqueId(), previousClassCacheEntry);
            }
            if (hadWarlockRecoilSetting) {
                warlockConfigSection.set("recoil.percent", previousWarlockRecoil);
            } else {
                warlockConfigSection.set("recoil.percent", null);
            }
            if (hadBlackMassEnabledSetting) {
                warlockConfigSection.set("passives.black_mass.enabled", previousBlackMassEnabled);
            } else {
                warlockConfigSection.set("passives.black_mass.enabled", null);
            }
            raskolPlugin.getRaskolConfig().kitCache().invalidate();
            blackMassContextPrepared = false;
        }
        remove(blackMassTarget);
        blackMassTarget = null;
        blackMassTargetId = null;
        if (source != null && source.isValid()) {
            source.remove();
        }
        if (target != null && target.isOnline()) {
            target.kickPlayer("RASKOL Paper runtime QA complete");
        }
    }
}

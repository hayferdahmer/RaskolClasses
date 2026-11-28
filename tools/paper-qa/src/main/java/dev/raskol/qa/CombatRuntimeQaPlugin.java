// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.qa;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.combat.CombatService;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.event.CustomHealEvent;
import dev.raskol.classes.effect.ActiveEffectManager;
import dev.raskol.classes.effect.EffectType;
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
import org.bukkit.attribute.Attribute;
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

    private static final double BURST_RESERVATION_TOLERANCE = 0.05;
    private static final double BLACK_MASS_HEAL_TOLERANCE = 0.001;

    private enum Scenario {
        CANCEL_PHYSICAL,
        CANCEL_MAGIC,
        CANCEL_BOTH,
        NESTED_HIT,
        INCREASE_PHYSICAL,
        REDUCE_PHYSICAL,
        ABSORPTION_ONLY
    }

    private CombatService combat;
    private RaskolClasses raskolPlugin;
    private LivingEntity blackMassTarget;
    private UUID blackMassTargetId;
    private boolean blackMassNestedTriggered;
    private boolean blackMassDirectNestedProbe;
    private int blackMassEvents;
    private int blackMassAttributedEvents;
    private double blackMassExpectedHeal;
    private double blackMassHealthBefore;
    private boolean blackMassCancelNextHit;
    private boolean blackMassCancelledHitObserved;
    private boolean blackMassHealCounting;
    private int blackMassCustomHealEvents;
    private double blackMassCustomHealAmount;
    private boolean blackMassCustomHealHealerNull;
    private boolean blackMassCustomHealCancelled;
    private boolean activeEffectOrderProbe;
    private UUID activeEffectOrderTargetId;
    private int activeEffectNestedEvents;
    private double activeEffectNestedBefore;
    private double activeEffectNestedAfter;
    private boolean activeEffectStealthAtNestedLowest;
    private double blackMassLifestealFraction;
    private double previousWarlockRecoil;
    private boolean previousBlackMassEnabled;
    private ConfigurationSection warlockConfigSection;
    private boolean hadWarlockRecoilSetting;
    private boolean hadBlackMassEnabledSetting;
    private Object previousClassCacheEntry;
    private Map<UUID, Object> classCacheEntries;
    private boolean blackMassContextPrepared;
    private boolean classCachePrepared;
    private Player target;
    private LivingEntity source;
    private Scenario activeScenario;
    private UUID targetId;
    private int physicalEvents;
    private int magicEvents;
    private int cancelledPhysical;
    private int cancelledMagic;
    private double initialPhysicalDamage;
    private double finalPhysicalDamage;
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
    private boolean targetStateCaptured;
    private GameMode originalGameMode;
    private double originalTargetHealth;
    private double originalTargetAbsorption;
    private int originalNoDamageTicks;
    private double originalMaxAbsorptionBase;
    private boolean absorptionAttributeOverridden;

    @Override
    public void onEnable() {
        suppressedTargetKey = new NamespacedKey(this, "suppressed-target");
        normalTargetKey = new NamespacedKey(this, "normal-target");
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, this::runSuppressionProbe, 20L);
        Bukkit.getScheduler().runTaskLater(this, () -> Stage4RuntimeCharacterization.run(this), 25L);
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
            if (blackMassDirectNestedProbe) {
                blackMassTarget.setNoDamageTicks(0);
                blackMassTarget.damage(1.0, target);
            } else {
                combat.dealDamage(blackMassTarget, target, new DamageProfile(1.0, 0.0, 0.0));
            }
            if (!target.getUniqueId().equals(CombatService.abilitySourceMark())) {
                failBlackMass("outer ability source UUID was not preserved after nested damage");
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void cancelBlackMassProbeHit(EntityDamageEvent event) {
        if (!blackMassCancelNextHit
                || blackMassTargetId == null
                || !blackMassTargetId.equals(event.getEntity().getUniqueId())
                || !(event instanceof EntityDamageByEntityEvent byEntity)
                || !byEntity.getDamager().getUniqueId().equals(target.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        blackMassCancelledHitObserved = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void observeBlackMassCustomHeal(CustomHealEvent event) {
        if (!blackMassHealCounting
                || target == null
                || !event.getTarget().getUniqueId().equals(target.getUniqueId())) {
            return;
        }
        blackMassCustomHealEvents++;
        blackMassCustomHealAmount += event.getAmount();
        blackMassCustomHealHealerNull |= event.getHealer() == null;
        blackMassCustomHealCancelled |= event.isCancelled();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void captureActiveEffectNestedBefore(EntityDamageByEntityEvent event) {
        if (!activeEffectOrderProbe
                || activeEffectOrderTargetId == null
                || !activeEffectOrderTargetId.equals(event.getEntity().getUniqueId())
                || target == null
                || !event.getDamager().getUniqueId().equals(target.getUniqueId())) {
            return;
        }
        activeEffectNestedEvents++;
        activeEffectNestedBefore = event.getDamage();
        activeEffectStealthAtNestedLowest = raskolPlugin.getEffects()
                .has(target.getUniqueId(), EffectType.STEALTH);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void captureActiveEffectNestedAfter(EntityDamageByEntityEvent event) {
        if (!activeEffectOrderProbe
                || activeEffectOrderTargetId == null
                || !activeEffectOrderTargetId.equals(event.getEntity().getUniqueId())
                || target == null
                || !event.getDamager().getUniqueId().equals(target.getUniqueId())) {
            return;
        }
        activeEffectNestedAfter = event.getDamage();
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

        if (physical) {
            initialPhysicalDamage = event.getDamage();
            if (activeScenario == Scenario.INCREASE_PHYSICAL) {
                event.setDamage(event.getDamage() * 2.0);
            } else if (activeScenario == Scenario.REDUCE_PHYSICAL) {
                event.setDamage(event.getDamage() * 0.5);
            }
        }

        boolean cancel = switch (activeScenario) {
            case CANCEL_PHYSICAL -> physical;
            case CANCEL_MAGIC -> magic;
            case CANCEL_BOTH -> true;
            case NESTED_HIT -> physical && !nestedTriggered;
            case INCREASE_PHYSICAL, REDUCE_PHYSICAL, ABSORPTION_ONLY -> false;
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void captureFinalCombatChannel(EntityDamageEvent event) {
        if (targetId == null || !targetId.equals(event.getEntity().getUniqueId())
                || !CombatService.isSuppressed()
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        finalPhysicalDamage = event.getDamage();
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
            originalBurstWindowPct = raskol.getConfig().getDouble("combat.burst-window-pct", 18.0);
            originalSingleHitPct = raskol.getConfig().getDouble("combat.max-single-hit-pct", 35.0);
            raskol.getConfig().set("combat.burst-window-pct", 1000.0);
            raskol.getConfig().set("combat.max-single-hit-pct", 1000.0);
            configOverridden = true;

            var maxAbsorptionAttribute = target.getAttribute(Attribute.MAX_ABSORPTION);
            if (maxAbsorptionAttribute == null) {
                fail("Player MAX_ABSORPTION attribute is unavailable");
                return;
            }
            originalGameMode = target.getGameMode();
            originalTargetHealth = target.getHealth();
            originalTargetAbsorption = target.getAbsorptionAmount();
            originalNoDamageTicks = target.getNoDamageTicks();
            originalMaxAbsorptionBase = maxAbsorptionAttribute.getBaseValue();
            targetStateCaptured = true;

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
        initialPhysicalDamage = 0.0;
        finalPhysicalDamage = 0.0;

        target.setHealth(target.getMaxHealth());
        if (scenario == Scenario.ABSORPTION_ONLY) {
            var maxAbsorptionAttribute = target.getAttribute(Attribute.MAX_ABSORPTION);
            if (maxAbsorptionAttribute == null) {
                fail("Player MAX_ABSORPTION attribute is unavailable for absorption-only scenario");
                return;
            }
            maxAbsorptionAttribute.setBaseValue(Math.max(originalMaxAbsorptionBase, 100.0));
            absorptionAttributeOverridden = true;
            target.setAbsorptionAmount(100.0);
            require(target.getAbsorptionAmount() > 0.001,
                    "MAX_ABSORPTION setup must allow a non-zero absorption amount; actual="
                            + target.getAbsorptionAmount());
            if (failed) {
                cleanup();
                return;
            }
        } else {
            target.setAbsorptionAmount(0.0);
        }
        target.setNoDamageTicks(0);
        combat.purgeBurstLog();

        double beforeHealth = target.getHealth();
        double before = target.getHealth() + target.getAbsorptionAmount();
        DamageProfile probeProfile = scenario == Scenario.ABSORPTION_ONLY
                ? new DamageProfile(2.0, 0.0, 0.0)
                : DamageProfile.hybrid(2.0, 2.0);
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
                + " health=" + beforeHealth
                + " absorption=" + target.getAbsorptionAmount());
        double reportedDamage = combat.dealDamage(target, source, probeProfile);
        double actualDamage = before - target.getHealth() - target.getAbsorptionAmount();
        getLogger().info("[RASKOL-COMBAT-QA] BURST_RESULT scenario=" + scenario
                + " reportedDamage=" + reportedDamage
                + " actualDefensivePoolDelta=" + actualDamage
                + " healthAfter=" + target.getHealth()
                + " absorptionAfter=" + target.getAbsorptionAmount());
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
                require(closeReservation(reserved, actualDamage / combat.scaleOf(target)), "reservation must equal actual applied magic damage; got " + reserved);
            }
            case CANCEL_MAGIC -> {
                require(physicalEvents == 1 && magicEvents == 1, "magic-cancel event counts");
                require(cancelledPhysical == 0 && cancelledMagic == 1, "magic-only cancellation");
                require(actualDamage > 0.001, "physical channel must apply after magic cancellation");
                require(closeReservation(reserved, actualDamage / combat.scaleOf(target)), "reservation must equal actual applied physical damage; got " + reserved);
            }
            case CANCEL_BOTH -> {
                require(physicalEvents == 1 && magicEvents == 1, "both-cancel event counts");
                require(cancelledPhysical == 1 && cancelledMagic == 1, "both channels must be cancelled");
                require(Math.abs(actualDamage) < 0.001, "cancelled channels must not change the defensive pool");
                require(closeReservation(reserved, 0.0), "fully cancelled hit must release its complete reservation; got " + reserved);
            }
            case NESTED_HIT -> {
                require(physicalEvents == 2 && magicEvents == 2, "nested event counts");
                require(cancelledPhysical == 1 && cancelledMagic == 0, "only the outer physical channel must be cancelled");
                require(actualDamage > 0.001, "nested and outer magic channels must apply");
                require(closeReservation(reserved, actualDamage / combat.scaleOf(target)), "nested and outer reservations must equal their actual combined defensive-pool delta; got " + reserved);
            }
            case INCREASE_PHYSICAL -> {
                require(physicalEvents == 1 && magicEvents == 1, "increased-damage event counts");
                require(cancelledPhysical == 0 && cancelledMagic == 0, "increased-damage channels must not be cancelled");
                require(finalPhysicalDamage > initialPhysicalDamage + 0.001,
                        "physical Paper damage must be increased by the QA listener: initial="
                                + initialPhysicalDamage + " final=" + finalPhysicalDamage);
                require(closeReservation(reserved, actualDamage / combat.scaleOf(target)),
                        "increased physical damage must be reconciled into the token; got " + reserved);
            }
            case REDUCE_PHYSICAL -> {
                require(physicalEvents == 1 && magicEvents == 1, "reduced-damage event counts");
                require(cancelledPhysical == 0 && cancelledMagic == 0, "reduced-damage channels must not be cancelled");
                require(finalPhysicalDamage < initialPhysicalDamage - 0.001,
                        "physical Paper damage must be reduced by the QA listener: initial="
                                + initialPhysicalDamage + " final=" + finalPhysicalDamage);
                require(closeReservation(reserved, actualDamage / combat.scaleOf(target)),
                        "reduced physical damage must release unused reservation; got " + reserved);
            }
            case ABSORPTION_ONLY -> {
                require(physicalEvents == 1 && magicEvents == 0, "absorption-only physical event counts");
                require(cancelledPhysical == 0 && cancelledMagic == 0, "absorption-only damage must not be cancelled");
                require(Math.abs(target.getHealth() - beforeHealth) < 0.001,
                        "absorption-only hit must not reduce health: before=" + beforeHealth
                                + " after=" + target.getHealth());
                require(target.getAbsorptionAmount() < 100.0,
                        "absorption-only hit must consume absorption: remaining=" + target.getAbsorptionAmount());
                require(actualDamage > 0.001,
                        "damage absorbed without health loss must still be attributed: delta=" + actualDamage);
                require(closeReservation(reserved, actualDamage / combat.scaleOf(target)),
                        "absorption-only reservation must equal consumed absorption; got " + reserved);
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
                + " actualDefensivePoolDelta=" + actualDamage
                + " reserved=" + reserved);

        if (scenario == Scenario.CANCEL_PHYSICAL) {
            Bukkit.getScheduler().runTaskLater(this, () -> runScenario(Scenario.CANCEL_MAGIC, 0), 80L);
        } else if (scenario == Scenario.CANCEL_MAGIC) {
            Bukkit.getScheduler().runTaskLater(this, () -> runScenario(Scenario.CANCEL_BOTH, 0), 80L);
        } else if (scenario == Scenario.CANCEL_BOTH) {
            Bukkit.getScheduler().runTaskLater(this, () -> runScenario(Scenario.NESTED_HIT, 0), 80L);
        } else if (scenario == Scenario.NESTED_HIT) {
            Bukkit.getScheduler().runTaskLater(this, () -> runScenario(Scenario.INCREASE_PHYSICAL, 0), 80L);
        } else if (scenario == Scenario.INCREASE_PHYSICAL) {
            Bukkit.getScheduler().runTaskLater(this, () -> runScenario(Scenario.REDUCE_PHYSICAL, 0), 80L);
        } else if (scenario == Scenario.REDUCE_PHYSICAL) {
            Bukkit.getScheduler().runTaskLater(this, () -> runScenario(Scenario.ABSORPTION_ONLY, 0), 80L);
        } else {
            getLogger().info("[RASKOL-COMBAT-QA] PATH_B_SETTLEMENT_MATRIX_PASS");
            getLogger().info("[RASKOL-COMBAT-QA] BURST_CANCELLATION_MATRIX_PASS");
            Bukkit.getScheduler().runTaskLater(this, this::runBlackMassNestedContextProbe, 20L);
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
        try {
            double previousReservations = reservationTotal(pathATargetId);
            clearReservations(pathATargetId);
            getLogger().info("[RASKOL-COMBAT-QA] PATH_A_LEDGER_ISOLATION_RESET previous=" + previousReservations);
        } catch (ReflectiveOperationException exception) {
            failPathA("Could not isolate Path A ledger from Path B reservations: " + exception);
            return;
        }
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

    private void clearReservations(UUID playerId) throws ReflectiveOperationException {
        Field ledgersField = combat.caps().getClass().getDeclaredField("burstLedgers");
        ledgersField.setAccessible(true);
        Map<?, ?> ledgers = (Map<?, ?>) ledgersField.get(combat.caps());
        Object ledger = ledgers.get(playerId);
        if (ledger == null) {
            return;
        }

        Field entriesField = ledger.getClass().getDeclaredField("entries");
        entriesField.setAccessible(true);
        Deque<?> entries = (Deque<?>) entriesField.get(ledger);
        synchronized (ledger) {
            entries.clear();
            ledgers.remove(playerId, ledger);
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
            blackMassContextPrepared = true;
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
            requireBlackMass(closeBlackMassHeal(actualHeal, blackMassExpectedHeal),
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
            runBlackMassDirectNestedProbe();
            if (failed) {
                cleanup();
                return;
            }
            runBlackMassCancelledHitProbe();
            if (failed) {
                cleanup();
                return;
            }
            runBlackMassMultiComponentProbe();
            if (failed) {
                cleanup();
                return;
            }
            runActiveEffectSameInstanceOrderProbe();
            if (failed) {
                cleanup();
                return;
            }
            startPathAMatrix();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            failBlackMass("Warlock integration probe threw: " + exception);
        }
    }

    /**
     * Проверяет прямой вложенный Entity.damage без дочернего DamageAttribution scope.
     */
    private void runBlackMassDirectNestedProbe() {
        target.setHealth(Math.max(1.0, target.getMaxHealth() - 5.0));
        blackMassTarget.setHealth(blackMassTarget.getMaxHealth());
        blackMassTarget.setNoDamageTicks(0);
        blackMassNestedTriggered = false;
        blackMassEvents = 0;
        blackMassAttributedEvents = 0;
        blackMassExpectedHeal = 0.0;
        blackMassCustomHealEvents = 0;
        blackMassCustomHealAmount = 0.0;
        blackMassCustomHealHealerNull = false;
        blackMassCustomHealCancelled = false;

        double playerHealthBefore = target.getHealth();
        double targetPoolBefore = blackMassTarget.getHealth() + blackMassTarget.getAbsorptionAmount();
        double reported;
        blackMassHealCounting = true;
        blackMassDirectNestedProbe = true;
        try {
            reported = combat.dealDamage(blackMassTarget, target, new DamageProfile(2.0, 0.0, 0.0));
        } finally {
            blackMassDirectNestedProbe = false;
            blackMassHealCounting = false;
        }

        double targetPoolAfter = blackMassTarget.getHealth() + blackMassTarget.getAbsorptionAmount();
        double actualDamage = targetPoolBefore - targetPoolAfter;
        double actualHeal = target.getHealth() - playerHealthBefore;
        if (!blackMassNestedTriggered) {
            failBlackMass("direct nested Entity.damage callback was not triggered");
            return;
        }
        if (blackMassEvents != 2 || blackMassAttributedEvents != 2 || blackMassCustomHealEvents != 2) {
            failBlackMass("direct nested event attribution mismatch: damageEvents=" + blackMassEvents
                    + " attributedEvents=" + blackMassAttributedEvents
                    + " customHealEvents=" + blackMassCustomHealEvents);
            return;
        }
        if (!closeReservation(reported, actualDamage)) {
            failBlackMass("parent DamageAttribution did not report the full unscoped nested pool delta: reported="
                    + reported + " actualDamage=" + actualDamage);
            return;
        }
        if (!closeBlackMassHeal(actualHeal, blackMassExpectedHeal)) {
            failBlackMass("direct nested event-time lifesteal mismatch: actual=" + actualHeal
                    + " expected=" + blackMassExpectedHeal);
            return;
        }

        getLogger().warning("[RASKOL-COMBAT-QA] BLACK_MASS_DIRECT_NESTED_CHARACTERIZED"
                + " state=UNSCOPED_DIRECT_NESTED_DELTA_INCLUDED_IN_PARENT"
                + " damageEvents=" + blackMassEvents
                + " customHealEvents=" + blackMassCustomHealEvents
                + " parentReported=" + reported
                + " totalPoolDelta=" + actualDamage
                + " actualHeal=" + actualHeal);
    }

    /**
     * Напрямую определяет порядок двух HIGH-обработчиков ActiveEffectManager:
     * входящий handler снимает STEALTH, а исходящий запускает BLOOD_FURY proc.
     * Nested proc наблюдает STEALTH только если исходящий handler вызвался первым.
     */
    private void runActiveEffectSameInstanceOrderProbe() {
        ActiveEffectManager effects = raskolPlugin.getEffects();
        UUID playerId = target.getUniqueId();
        effects.remove(playerId, EffectType.STEALTH);
        effects.remove(playerId, EffectType.BLOOD_FURY);
        effects.addOneShot(playerId, EffectType.STEALTH);
        effects.addTimed(playerId, EffectType.BLOOD_FURY, 60_000L);

        LivingEntity aggressor = (LivingEntity) spawnPassiveMob(
                target.getWorld(), target.getLocation().clone().add(2.0, 0.0, 0.0));
        aggressor.setNoDamageTicks(0);
        target.setNoDamageTicks(0);
        target.setHealth(target.getMaxHealth());

        activeEffectOrderTargetId = aggressor.getUniqueId();
        activeEffectNestedEvents = 0;
        activeEffectNestedBefore = 0.0;
        activeEffectNestedAfter = 0.0;
        activeEffectStealthAtNestedLowest = false;
        activeEffectOrderProbe = true;
        try {
            target.damage(4.0, aggressor);
        } finally {
            activeEffectOrderProbe = false;
            activeEffectOrderTargetId = null;
            effects.remove(playerId, EffectType.STEALTH);
            effects.remove(playerId, EffectType.BLOOD_FURY);
            remove(aggressor);
        }

        if (activeEffectNestedEvents != 1
                || activeEffectNestedBefore <= 0.0
                || activeEffectNestedAfter <= 0.0) {
            failBlackMass("ActiveEffectManager order probe did not observe exactly one nested Blood Fury event: events="
                    + activeEffectNestedEvents + " before=" + activeEffectNestedBefore
                    + " after=" + activeEffectNestedAfter);
            return;
        }

        double ratio = activeEffectNestedAfter / activeEffectNestedBefore;
        String state = activeEffectStealthAtNestedLowest
                ? "OUTGOING_HIGH_BEFORE_INCOMING_HIGH"
                : "INCOMING_HIGH_BEFORE_OUTGOING_HIGH";
        boolean ratioMatches = activeEffectStealthAtNestedLowest
                ? Math.abs(ratio - 1.5) <= 0.05
                : Math.abs(ratio - 1.0) <= 0.05;
        if (!ratioMatches) {
            failBlackMass("ActiveEffectManager same-instance order signal disagrees with nested LOW multiplier: state="
                    + state + " ratio=" + ratio);
            return;
        }

        getLogger().warning("[RASKOL-COMBAT-QA] ACTIVE_EFFECT_SAME_INSTANCE_ORDER_CHARACTERIZED"
                + " state=" + state
                + " stealthAtNestedLowest=" + activeEffectStealthAtNestedLowest
                + " nestedDamageBeforeLow=" + activeEffectNestedBefore
                + " nestedDamageAtMonitor=" + activeEffectNestedAfter
                + " lowMultiplierRatio=" + ratio);
    }

    /**
     * Проверяет текущее HIGH-побочное действие, если последующий HIGHEST-обработчик
     * отменяет исходный hit Path B.
     */
    private void runBlackMassCancelledHitProbe() {
        target.setHealth(Math.max(1.0, target.getMaxHealth() - 5.0));
        blackMassTarget.setHealth(blackMassTarget.getMaxHealth());
        blackMassTarget.setNoDamageTicks(0);
        blackMassNestedTriggered = true;
        blackMassEvents = 0;
        blackMassAttributedEvents = 0;
        blackMassExpectedHeal = 0.0;
        blackMassCustomHealEvents = 0;
        blackMassCustomHealAmount = 0.0;
        blackMassCustomHealHealerNull = false;
        blackMassCustomHealCancelled = false;
        blackMassCancelledHitObserved = false;

        double playerHealthBefore = target.getHealth();
        double targetPoolBefore = blackMassTarget.getHealth() + blackMassTarget.getAbsorptionAmount();
        double reported;
        blackMassHealCounting = true;
        blackMassCancelNextHit = true;
        try {
            reported = combat.dealDamage(blackMassTarget, target, new DamageProfile(2.0, 0.0, 0.0));
        } finally {
            blackMassCancelNextHit = false;
            blackMassHealCounting = false;
        }

        double targetPoolAfter = blackMassTarget.getHealth() + blackMassTarget.getAbsorptionAmount();
        double actualDamage = targetPoolBefore - targetPoolAfter;
        double actualHeal = target.getHealth() - playerHealthBefore;
        if (!blackMassCancelledHitObserved) {
            failBlackMass("HIGHEST cancellation probe did not cancel the originating Path B event");
            return;
        }
        if (Math.abs(reported) > 0.05 || Math.abs(actualDamage) > 0.05) {
            failBlackMass("Cancelled Path B hit applied damage: reported=" + reported
                    + " actualDamage=" + actualDamage);
            return;
        }

        String state = blackMassCustomHealEvents == 0
                ? "NO_CUSTOM_HEAL_EVENT"
                : actualHeal > 0.001 ? "HEAL_EVENT_ON_CANCELLED_HIT" : "HEAL_EVENT_NOT_APPLIED";
        getLogger().warning("[RASKOL-COMBAT-QA] BLACK_MASS_CANCELLED_HIT_CHARACTERIZED"
                + " state=" + state
                + " damageEvents=" + blackMassEvents
                + " customHealEvents=" + blackMassCustomHealEvents
                + " actualDamage=" + actualDamage
                + " actualHeal=" + actualHeal
                + " expectedHeal=" + blackMassExpectedHeal
                + " customHealAmount=" + blackMassCustomHealAmount
                + " healerNull=" + blackMassCustomHealHealerNull
                + " healCancelled=" + blackMassCustomHealCancelled);
    }

    /**
     * Проверяет, создаёт ли один вызов dealDamage с physical+magic один или несколько
     * событий CustomHealEvent для Black Mass.
     */
    private void runBlackMassMultiComponentProbe() {
        target.setHealth(Math.max(1.0, target.getMaxHealth() - 5.0));
        blackMassTarget.setHealth(blackMassTarget.getMaxHealth());
        blackMassTarget.setNoDamageTicks(0);
        blackMassNestedTriggered = true;
        blackMassEvents = 0;
        blackMassAttributedEvents = 0;
        blackMassExpectedHeal = 0.0;
        blackMassCustomHealEvents = 0;
        blackMassCustomHealAmount = 0.0;
        blackMassCustomHealHealerNull = false;
        blackMassCustomHealCancelled = false;

        double playerHealthBefore = target.getHealth();
        double targetPoolBefore = blackMassTarget.getHealth() + blackMassTarget.getAbsorptionAmount();
        double reported;
        blackMassHealCounting = true;
        try {
            reported = combat.dealDamage(blackMassTarget, target, DamageProfile.hybrid(1.0, 1.0));
        } finally {
            blackMassHealCounting = false;
        }

        double targetPoolAfter = blackMassTarget.getHealth() + blackMassTarget.getAbsorptionAmount();
        double actualDamage = targetPoolBefore - targetPoolAfter;
        double actualHeal = target.getHealth() - playerHealthBefore;
        if (reported <= 0.0 || actualDamage <= 0.0) {
            failBlackMass("Hybrid Black Mass characterization did not apply damage: reported="
                    + reported + " actualDamage=" + actualDamage);
            return;
        }

        String state = blackMassCustomHealEvents > 1
                ? "MULTIPLE_HEAL_EVENTS_PER_INVOCATION"
                : blackMassCustomHealEvents == 1 ? "ONE_HEAL_EVENT_PER_INVOCATION" : "NO_CUSTOM_HEAL_EVENT";
        getLogger().warning("[RASKOL-COMBAT-QA] BLACK_MASS_MULTI_COMPONENT_CHARACTERIZED"
                + " state=" + state
                + " damageEvents=" + blackMassEvents
                + " attributedEvents=" + blackMassAttributedEvents
                + " customHealEvents=" + blackMassCustomHealEvents
                + " actualDamage=" + actualDamage
                + " reported=" + reported
                + " actualHeal=" + actualHeal
                + " customHealAmount=" + blackMassCustomHealAmount
                + " healerNull=" + blackMassCustomHealHealerNull);
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
        classCachePrepared = true;

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

    private boolean closeReservation(double actual, double expected) {
        return Math.abs(actual - expected) < BURST_RESERVATION_TOLERANCE;
    }

    private boolean closeBlackMassHeal(double actual, double expected) {
        return Math.abs(actual - expected) < BLACK_MASS_HEAL_TOLERANCE;
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
        if (classCachePrepared) {
            if (previousClassCacheEntry == null) {
                classCacheEntries.remove(target.getUniqueId());
            } else {
                classCacheEntries.put(target.getUniqueId(), previousClassCacheEntry);
            }
            classCachePrepared = false;
        }
        if (blackMassContextPrepared) {
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
        if (targetStateCaptured && target != null && target.isOnline()) {
            var maxAbsorptionAttribute = target.getAttribute(Attribute.MAX_ABSORPTION);
            if (maxAbsorptionAttribute != null && absorptionAttributeOverridden) {
                maxAbsorptionAttribute.setBaseValue(originalMaxAbsorptionBase);
                absorptionAttributeOverridden = false;
            }
            target.setGameMode(originalGameMode);
            target.setHealth(Math.min(originalTargetHealth, target.getMaxHealth()));
            double absorptionLimit = maxAbsorptionAttribute == null
                    ? 0.0
                    : maxAbsorptionAttribute.getValue();
            target.setAbsorptionAmount(Math.min(originalTargetAbsorption, absorptionLimit));
            target.setNoDamageTicks(originalNoDamageTicks);
            targetStateCaptured = false;
        }
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

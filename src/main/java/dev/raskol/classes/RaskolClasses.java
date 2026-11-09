// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.ability.AbilityRegistry;
import dev.raskol.classes.ability.CooldownManager;
import dev.raskol.classes.ability.HunterAbilities;
import dev.raskol.classes.ability.TreeAbilities;
import dev.raskol.classes.ability.WarlockAbilities;
import dev.raskol.classes.ability.passive.PassiveListener;
import dev.raskol.classes.api.combat.CombatProfileLifecycleListener;
import dev.raskol.classes.api.combat.CombatProfileRegistry;
import dev.raskol.classes.api.combat.EntityLevelProvider;
import dev.raskol.classes.api.items.ItemsApi;
import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.attribute.HpAttributeSync;
import dev.raskol.classes.bootstrap.PluginTasks;
import dev.raskol.classes.bootstrap.SessionListener;
import dev.raskol.classes.bootstrap.StartupChecks;
import dev.raskol.classes.cc.CCGuard;
import dev.raskol.classes.cc.CCService;
import dev.raskol.classes.cc.CastGuard;
import dev.raskol.classes.cc.VanillaCCWrapper;
import dev.raskol.classes.classsystem.CharacterLevelService;
import dev.raskol.classes.classsystem.ClassProvider;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.combat.CombatService;
import dev.raskol.classes.combat.ManaSoakedService;
import dev.raskol.classes.combat.ResistService;
import dev.raskol.classes.command.RaskolCommand;
import dev.raskol.classes.config.ConfigMigrator;
import dev.raskol.classes.config.ConfigValidator;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.effect.ActiveEffectManager;
import dev.raskol.classes.flavor.CrownFlavorService;
import dev.raskol.classes.foliant.FoliantService;
import dev.raskol.classes.fx.FxService;
import dev.raskol.classes.fx.TrailListener;
import dev.raskol.classes.gui.ClassBook;
import dev.raskol.classes.gui.PlusGui;
import dev.raskol.classes.hook.BlueprintHook;
import dev.raskol.classes.hook.FactionHook;
import dev.raskol.classes.hook.FlavorPlaceholder;
import dev.raskol.classes.hook.PassportChangeListener;
import dev.raskol.classes.hotbar.AbilityToken;
import dev.raskol.classes.hotbar.BindListener;
import dev.raskol.classes.hotbar.ScrollCooldownTask;
import dev.raskol.classes.hotbar.ScrollSanitizer;
import dev.raskol.classes.hud.BossBarService;
import dev.raskol.classes.hud.HpBarService;
import dev.raskol.classes.hud.HudService;
import dev.raskol.classes.install.InstallBindListener;
import dev.raskol.classes.install.InstallToken;
import dev.raskol.classes.install.InstallationService;
import dev.raskol.classes.integrity.artifact.ArtifactBoundIntegrityVerifier;
import dev.raskol.classes.integrity.policy.IntegrityFailurePolicy;
import dev.raskol.classes.integrity.policy.RuntimeIntegrityEnforcementPolicy;
import dev.raskol.classes.integrity.reporting.SecurityReporter;
import dev.raskol.classes.integrity.runtime.RuntimeIntegrityMonitor;
import dev.raskol.classes.integrity.runtime.RuntimeIntegritySnapshot;
import dev.raskol.classes.integrity.runtime.RuntimeIntegrityState;
import dev.raskol.classes.integrity.runtime.RuntimeIntegrityStateManager;
import dev.raskol.classes.integrity.runtime.RuntimeSecurityReporter;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;
import dev.raskol.classes.item.service.ItemSystem;
import dev.raskol.classes.pet.PetService;
import dev.raskol.classes.resource.ResourceService;
import dev.raskol.classes.spec.build.BuildTemplates;
import dev.raskol.classes.spec.build.ImperialWallet;
import dev.raskol.classes.spec.build.NullWallet;
import dev.raskol.classes.spec.build.PlayerPointsWallet;
import dev.raskol.classes.spec.build.PlusStorage;
import dev.raskol.classes.spec.build.PlusSubscription;
import dev.raskol.classes.spec.listen.Spec2RoleListener;
import dev.raskol.classes.spec.model.Spec2Points;
import dev.raskol.classes.spec.service.Spec2EffectsApplier;
import dev.raskol.classes.spec.service.Spec2Service;
import dev.raskol.classes.spec.storage.Spec2Storage;

/**
 * RaskolClasses — «РАСКОЛ | ДВЕ КОРОНЫ». Точка сборки сервиса: конструирование
 * слоёв, регистрация слушателей, владение периодическими задачами и геттеры-локаторы.
 *
 * 1.15.7 (Волна 7d): PetService.despawnAll() вызывается в onDisable до отмены задач —
 * петы дезспавнятся при выгрузке плагина, а не остаются в мире.
 * 1.15.7 (Волна 7c): регистрация HunterAbilities.FanArrowHit — стрелы веера наносят
 * урон через dealDamage (проходят resist-систему).
 * 1.15.7 (Волна 7a): AvoidanceService регистрируется как Listener — снимает
 * parry_stagger на quit/join.
 * 1.15.6 (Волна 7b): CCFeedback переработан — голограммы TextDisplay, сообщения,
 * звуки по типам CC; clearHologram вызывается из CCService.clearBehavior.
 * 1.15.6 (Волна 5): ActiveEffectManager получает plugin через конструктор —
 * BLOOD_FURY через dealDamage, а не прямой aggressor.damage(...).
 * 1.14.15: слой подписки на кураторские билды — ImperialWallet, PlusStorage,
 * BuildTemplates, PlusSubscription, PlusGui.
 * 1.14.14: WarlockAbilities создаётся через статическую фабрику install().
 * 1.14.13: побочные эффекты конструкторов перенесены в onEnable (registerListeners,
 * startTickTask, scheduleOrphanScan, startLockFlushTask).
 * 1.14.10: декомпозиция God Object'а — startup-проверки в bootstrap.StartupChecks,
 * периодические задачи в bootstrap.PluginTasks, слушатели входа/выхода в bootstrap.SessionListener.
 * 1.14.8: ConfigMigrator.migrate148() в начале onEnable.
 * 1.14.6: PetService — единое ядро боевых петов (wolf/demon/shadowfiend).
 * 1.14.4: Spec2Points.configure(this) до инициализации spec2-слоя.
 * 1.14.0: spec2-слой (деревья путей), legacy TalentService/SpecService удалены.
 * 1.13.0: CC-слой (CCService/CCGuard/CastGuard).
 */
public final class RaskolClasses extends JavaPlugin {

    private RaskolConfig raskolConfig;
    private ClassProvider classProvider;
    private SkillLevelProvider skillLevels;
    private CharacterLevelService characterLevels;
    private ResourceService resources;
    private CooldownManager cooldowns;
    private AbilityRegistry abilities;
    private ActiveEffectManager effects;
    private HudService hud;
    private BossBarService bossBars;
    private HpBarService hpBarService;
    private AbilityToken tokens;

    /** 1.14.0: spec2-слой (деревья путей). */
    private Spec2Storage spec2Storage;

    private Spec2Service spec2Service;
    private Spec2EffectsApplier spec2Applier;

    private FactionHook factionHook;
    private CrownFlavorService flavorService;

    private ItemSystem items;
    private ItemsApi itemsApi;
    private CombatProfileRegistry combatProfiles;
    private EntityLevelProvider entityLevelProvider;
    private BlueprintHook blueprintHook;
    private FoliantService foliantService;

    private FxService fx;

    private InstallationService installations;
    private InstallToken installToken;

    private ResistService resists;
    private CombatService combat;
    private ManaSoakedService manaSoaked;
    private ConfigValidator configValidator;
    private AttributeService attributes;

    private HpAttributeSync hpSync;

    private PassiveListener passiveListener;
    private PassportChangeListener passportListener;

    private WarlockAbilities warlockAbilities;

    /** 1.14.6: пет-слой. */
    private PetService pets;

    private CCService ccService;
    private CCGuard ccGuard;
    private CastGuard castGuard;
    private VanillaCCWrapper vanillaCCWrapper;

    /** 1.14.15: raskolspec+ — подписка на кураторские билды. */
    private BuildTemplates buildTemplates;

    private ImperialWallet imperialWallet;
    private PlusSubscription plusSubscription;
    private PlusGui plusGui;

    private volatile long lastPurgeMillis = System.currentTimeMillis();
    private final long enabledAtMillis = System.currentTimeMillis();

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    /** RIPS verifier shared by startup and runtime checks. */
    private ArtifactBoundIntegrityVerifier artifactBoundIntegrityVerifier;

    /** Result of the RIPS startup integrity verification. */
    private IntegrityVerificationResult integrityVerificationResult;

    /** RIPS runtime integrity monitor; started only after successful plugin initialization. */
    private RuntimeIntegrityMonitor runtimeIntegrityMonitor;

    /** Runtime policy for classifying integrity failures. */
    private final RuntimeIntegrityEnforcementPolicy runtimeIntegrityEnforcementPolicy =
            new RuntimeIntegrityEnforcementPolicy();

    /**
     * Prevents multiple runtime integrity failures from scheduling
     * multiple plugin-disable tasks.
     */
    private final AtomicBoolean runtimeIntegrityFailureScheduled = new AtomicBoolean(false);

    /**
     * Runs artifact-bound RIPS verification before gameplay initialization.
     *
     * @return the startup integrity verification result
     */
    private IntegrityVerificationResult verifyStartupIntegrity() {
        this.artifactBoundIntegrityVerifier = new ArtifactBoundIntegrityVerifier(RaskolClasses.class);

        IntegrityVerificationResult result = artifactBoundIntegrityVerifier.verify();

        SecurityReporter reporter = new SecurityReporter(getLogger());
        reporter.report(result);

        return result;
    }

    @Override
    public void onEnable() {
        this.integrityVerificationResult = verifyStartupIntegrity();

        IntegrityFailurePolicy failurePolicy = new IntegrityFailurePolicy();
        if (!failurePolicy.mayContinueStartup(integrityVerificationResult)) {
            getLogger()
                    .severe("[RIPS] Startup denied: integrity verification failed. Status: "
                            + integrityVerificationResult.status());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.raskolConfig = new RaskolConfig(this);
        ConfigMigrator.migrate148(this);

        getLogger()
                .info(() -> "Paper: " + getServer().getVersion() + " / Bukkit: "
                        + getServer().getBukkitVersion());

        StartupChecks.logCoreVersion(this);
        StartupChecks.cleanupTmpFiles(this);

        PluginManager pluginManager = getServer().getPluginManager();

        this.classProvider = new ClassProvider(this, raskolConfig);
        if (!classProvider.isAvailable()) {
            getLogger().warning("LuckPerms не найден — определение классов отключено");
        }

        this.items = new ItemSystem(this);
        this.itemsApi = new ItemsApi(this);
        this.combatProfiles = new CombatProfileRegistry();
        pluginManager.registerEvents(new CombatProfileLifecycleListener(combatProfiles), this);

        this.blueprintHook = new BlueprintHook(this);
        blueprintHook.registerBlueprints();

        this.skillLevels = new SkillLevelProvider(this);
        this.characterLevels = new CharacterLevelService(this);
        this.resources = new ResourceService(this, raskolConfig, classProvider);

        classProvider.setResourceService(resources);

        this.cooldowns = new CooldownManager(new File(getDataFolder(), "cooldowns.yml"));
        this.cooldowns.attachScheduler(
                this,
                raskolConfig.isReadyNotifyEnabled(),
                raskolConfig.readyNotifyMinCooldownSeconds(),
                raskolConfig.readyNotifySoundKey(),
                raskolConfig.readyNotifyMessage());
        this.effects = new ActiveEffectManager(this);

        this.abilities = new AbilityRegistry(this);
        abilities.loadFromConfig(raskolConfig);
        TreeAbilities.registerCasters(this, abilities);

        this.hud = new HudService(this);
        this.bossBars = new BossBarService(this);
        this.tokens = new AbilityToken(this);

        this.fx = new FxService(this);
        fx.validateConfig();

        this.resists = new ResistService(this);
        this.combat = new CombatService(this, resists);
        if (getConfig().getBoolean("item.enabled", true)) {
            items.start();
        }
        combat.dots().registerListeners();
        activeTasks.add(combat.dots().startTickTask());
        this.manaSoaked = new ManaSoakedService(this);

        this.ccService = new CCService(this);
        ccService.registerListeners();
        activeTasks.add(ccService.startTickTask());

        this.ccGuard = new CCGuard(this);
        ccGuard.registerListeners();

        this.castGuard = new CastGuard(this);

        this.vanillaCCWrapper = new VanillaCCWrapper(this);
        vanillaCCWrapper.registerListeners();

        this.configValidator = new ConfigValidator(this);
        configValidator.validate();
        configValidator.logSummary();

        this.attributes = new AttributeService(this);
        this.hpSync = new HpAttributeSync(this);
        pluginManager.registerEvents(hpSync, this);
        hpSync.startSweep(100L);
        this.hpBarService = new HpBarService(this);

        Spec2Points.configure(this);

        this.spec2Storage = new Spec2Storage(this);
        this.spec2Applier = new Spec2EffectsApplier(this);
        this.spec2Service = new Spec2Service(this, spec2Storage);
        this.spec2Service.refreshDisabledCache();
        pluginManager.registerEvents(new Spec2RoleListener(this), this);

        ImperialWallet wallet = PlayerPointsWallet.tryCreate();
        if (wallet == null) {
            wallet = new NullWallet();
            getLogger().info("raskolspec+: PlayerPoints недоступен, используется NullWallet");
        }
        this.imperialWallet = wallet;
        this.buildTemplates = new BuildTemplates(this);
        buildTemplates.extractIfMissing();
        buildTemplates.load();
        PlusStorage plusStorage = new PlusStorage(getDataFolder());
        int periodDays = getConfig().getInt("plus.period-days", 30);
        this.plusSubscription = new PlusSubscription(this, imperialWallet, plusStorage, periodDays);
        pluginManager.registerEvents(plusSubscription, this);

        this.plusGui = new PlusGui(this);
        pluginManager.registerEvents(plusGui, this);

        this.factionHook = new FactionHook(this);
        this.flavorService = new CrownFlavorService(this, factionHook);

        this.installations = new InstallationService(this);
        this.installToken = new InstallToken(this);

        this.foliantService = new FoliantService(this);
        activeTasks.add(foliantService.startLockFlushTask());

        this.warlockAbilities = WarlockAbilities.install(this);

        this.pets = new PetService(this);
        pets.registerListeners();
        activeTasks.add(pets.startTickTask());
        activeTasks.add(pets.scheduleOrphanScan());

        pluginManager.registerEvents(resources, this);
        pluginManager.registerEvents(effects, this);
        pluginManager.registerEvents(cooldowns, this);
        this.passiveListener = new PassiveListener(this);
        pluginManager.registerEvents(passiveListener, this);
        pluginManager.registerEvents(new ClassBook.ClickHandler(this), this);
        pluginManager.registerEvents(new BindListener(this, tokens), this);
        pluginManager.registerEvents(flavorService, this);
        pluginManager.registerEvents(new TrailListener(this), this);
        pluginManager.registerEvents(new InstallBindListener(this, installToken), this);
        pluginManager.registerEvents(combat, this);
        pluginManager.registerEvents(combat.vanillaListener(), this);
        pluginManager.registerEvents(combat.avoidance(), this);
        pluginManager.registerEvents(new HunterAbilities.FanArrowHit(this), this);
        pluginManager.registerEvents(new ScrollSanitizer(this), this);
        pluginManager.registerEvents(hpBarService, this);
        pluginManager.registerEvents(fx, this);
        pluginManager.registerEvents(foliantService, this);
        pluginManager.registerEvents(warlockAbilities, this);

        this.passportListener = new PassportChangeListener(this);
        passportListener.register();

        SessionListener sessions = new SessionListener(this);
        pluginManager.registerEvents(sessions, this);
        sessions.resyncOnline();

        if (pluginManager.getPlugin("PlaceholderAPI") != null) {
            new dev.raskol.classes.hook.RaskolPlaceholder(this).register();
            new FlavorPlaceholder(this).register();
            getLogger().info("PlaceholderAPI: плейсхолдеры %raskolclasses_*% " + "и %raskolcrown_*% зарегистрированы");
        } else {
            getLogger().warning("PlaceholderAPI не найден: плейсхолдеры не регистрируются");
        }

        boolean hudEnabled = getConfig().getBoolean("hud.enabled", true);
        boolean hpVanilla = "vanilla".equalsIgnoreCase(getConfig().getString("hp-display.mode", "actionbar"));
        if (hudEnabled && hpVanilla) {
            activeTasks.add(hud.start());
        }
        activeTasks.add(hpBarService.start());
        activeTasks.add(resources.startTickTask(this));
        activeTasks.add(bossBars.start());
        activeTasks.add(flavorService.startAuraTask());
        activeTasks.add(installations.startSweepTask());
        activeTasks.add(new ScrollCooldownTask(this).start());
        activeTasks.add(manaSoaked.start());
        activeTasks.add(PluginTasks.startPurgeTask(this));
        activeTasks.add(PluginTasks.startAutosaveTask(this));

        registerCommand();
        StartupChecks.printBanner(this);
        StartupChecks.warnLowCapacityTrees(this);

        startRuntimeIntegrityMonitoring();

        getLogger().info(() -> "RaskolClasses v" + getPluginMeta().getVersion() + " запущен");
    }

    /**
     * Starts periodic artifact-bound RIPS verification after plugin startup.
     *
     * <p>Runtime checks run on a dedicated daemon thread. Bukkit/Paper
     * lifecycle operations are dispatched to the server main thread.</p>
     */
    private void startRuntimeIntegrityMonitoring() {
        if (artifactBoundIntegrityVerifier == null) {
            getLogger().severe("[RIPS] Runtime monitoring was not started: verifier is unavailable.");
            return;
        }

        RuntimeSecurityReporter runtimeReporter =
                new RuntimeSecurityReporter(new SecurityReporter(getLogger()), getLogger());

        this.runtimeIntegrityMonitor = new RuntimeIntegrityMonitor(
                () -> artifactBoundIntegrityVerifier.verify(),
                Duration.ofMinutes(30),
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "RaskolClasses-RIPS-Monitor");
                    thread.setDaemon(true);
                    return thread;
                }),
                new RuntimeIntegrityStateManager(),
                runtimeReporter,
                this::handleRuntimeIntegrityFailure);

        runtimeIntegrityMonitor.start();
        getLogger().info("[RIPS] Artifact-bound runtime integrity monitoring started (interval: 30 minutes)");
    }

    /**
     * Handles a runtime integrity failure.
     *
     * <p>Confirmed integrity violations trigger immediate shutdown.
     * Temporary verification failures are tolerated up to the configured
     * consecutive failure threshold.</p>
     *
     * <p>This method is invoked by the background monitoring thread.
     * Plugin lifecycle operations are dispatched to the Paper main thread.</p>
     *
     * @param snapshot failed runtime integrity snapshot
     */
    private void handleRuntimeIntegrityFailure(RuntimeIntegritySnapshot snapshot) {
        if (snapshot == null || snapshot.state() != RuntimeIntegrityState.FAILED) {
            return;
        }

        if (!runtimeIntegrityEnforcementPolicy.shouldDisable(snapshot)) {
            String status = snapshot.lastResult() == null
                    ? "VERIFICATION_EXECUTION_ERROR"
                    : snapshot.lastResult().status().name();

            getLogger()
                    .warning("[RIPS] Runtime verification failure tolerated. "
                            + "Status: " + status
                            + ", consecutiveFailures="
                            + snapshot.consecutiveFailures()
                            + "/"
                            + runtimeIntegrityEnforcementPolicy.getTransientFailureThreshold());

            return;
        }

        if (!runtimeIntegrityFailureScheduled.compareAndSet(false, true)) {
            return;
        }

        try {
            getServer().getScheduler().runTask(this, () -> {
                if (!isEnabled()) {
                    return;
                }

                String status = snapshot.lastResult() == null
                        ? "VERIFICATION_EXECUTION_ERROR"
                        : snapshot.lastResult().status().name();

                getLogger()
                        .severe("[RIPS] Runtime integrity enforcement triggered. "
                                + "Status: " + status
                                + ", consecutiveFailures="
                                + snapshot.consecutiveFailures());

                getLogger()
                        .severe("[RIPS] Disabling RaskolClasses because runtime " + "integrity verification failed.");

                getServer().getPluginManager().disablePlugin(this);
            });
        } catch (RuntimeException exception) {
            runtimeIntegrityFailureScheduled.set(false);

            getLogger()
                    .severe("[RIPS] Unable to schedule plugin shutdown on the Paper " + "main thread: "
                            + exception.getMessage());
        }
    }

    public void markPurgeNow() {
        lastPurgeMillis = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        if (runtimeIntegrityMonitor != null) {
            runtimeIntegrityMonitor.close();
            runtimeIntegrityMonitor = null;
        }

        if (hpSync != null) {
            hpSync.stopSweep();
        }
        if (blueprintHook != null) {
            blueprintHook.unregisterBlueprints();
        }
        WarlockAbilities.cancelAllChannelTasks();

        if (foliantService != null) {
            foliantService.saveLocksIfDirty();
        }

        // 1.15.7 (Волна 7d): петы дезспавнятся до отмены задач
        if (pets != null) {
            pets.despawnAll();
        }

        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
        if (items != null) items.stop();
        if (passportListener != null) {
            passportListener.unregister();
        }
        if (resources != null) {
            resources.saveAll();
        }
        if (hpBarService != null) {
            hpBarService.saveAll();
        }
        if (spec2Storage != null) {
            spec2Storage.save();
        }
        if (installations != null) {
            installations.shutdown();
        }
        if (bossBars != null) {
            bossBars.shutdown();
        }
        if (cooldowns != null) {
            cooldowns.cancelAllTasks();
            cooldowns.saveAll();
            cooldowns.clear();
        }
        if (classProvider != null) {
            classProvider.shutdown();
        }
        if (effects != null) {
            effects.clear();
        }
    }

    private void registerCommand() {
        PluginCommand command = getCommand("rc");
        if (command == null) {
            getLogger().warning("Команда rc не описана в plugin.yml — каст недоступен");
            return;
        }
        RaskolCommand executor = new RaskolCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public dev.raskol.classes.bootstrap.PluginReloadResult reloadPlugin() {
        String stage = "config.yml";
        try {
            raskolConfig.reload();

            stage = "настройки специализаций";
            Spec2Points.configure(this);
            if (spec2Service != null) {
                spec2Service.refreshDisabledCache();
            }

            stage = "шаблоны raskolspec+";
            if (buildTemplates != null) {
                buildTemplates.reload();
            }

            stage = "настройки комплектов";
            raskolConfig.reloadKits();

            stage = "способности";
            abilities.loadFromConfig(raskolConfig);

            stage = "HUD";
            hud.applyConfig();
            cooldowns.attachScheduler(
                    this,
                    raskolConfig.isReadyNotifyEnabled(),
                    raskolConfig.readyNotifyMinCooldownSeconds(),
                    raskolConfig.readyNotifySoundKey(),
                    raskolConfig.readyNotifyMessage());
            if (bossBars != null) {
                bossBars.applyConfig();
            }

            stage = "визуальные эффекты и проверка конфигурации";
            fx.validateConfig();
            configValidator.validate();

            stage = "предметы и крафт";
            if (items != null) {
                dev.raskol.classes.bootstrap.PluginReloadResult itemReload =
                        dev.raskol.classes.bootstrap.PluginReloadResult.itemsReload(items::reload);
                if (!itemReload.successful()) {
                    getLogger().severe("Перезагрузка завершена с ошибкой: " + itemReload.message());
                    return itemReload;
                }
            }

            stage = "синхронизация игроков";
            for (org.bukkit.entity.Player online : getServer().getOnlinePlayers()) {
                spec2Service.reconcile(online.getUniqueId());
                if (items != null) {
                    items.reconcile(online);
                }
            }
            if (hpSync != null) {
                hpSync.syncAll();
            }

            getLogger().info("Конфигурация перезагружена");
            return dev.raskol.classes.bootstrap.PluginReloadResult.success();
        } catch (RuntimeException ex) {
            getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Перезагрузка остановлена на этапе «" + stage + "». Часть настроек могла примениться.",
                            ex);
            return dev.raskol.classes.bootstrap.PluginReloadResult.failure(
                    stage, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    public long getLastPurgeMillis() {
        return lastPurgeMillis;
    }

    public long getEnabledAtMillis() {
        return enabledAtMillis;
    }

    public RaskolConfig getRaskolConfig() {
        return raskolConfig;
    }

    public ClassProvider getClassProvider() {
        return classProvider;
    }

    public SkillLevelProvider getSkillLevels() {
        return skillLevels;
    }

    public CharacterLevelService getCharacterLevels() {
        return characterLevels;
    }

    public ResourceService getResources() {
        return resources;
    }

    public CooldownManager getCooldowns() {
        return cooldowns;
    }

    public AbilityRegistry getAbilities() {
        return abilities;
    }

    public AbilityRegistry getAbilityRegistry() {
        return abilities;
    }

    public ActiveEffectManager getEffects() {
        return effects;
    }

    public HudService getHud() {
        return hud;
    }

    public BossBarService getBossBars() {
        return bossBars;
    }

    public HpBarService getHpBarService() {
        return hpBarService;
    }

    public AbilityToken getTokens() {
        return tokens;
    }

    public Spec2Storage getSpec2Storage() {
        return spec2Storage;
    }

    public Spec2Service getSpec2Service() {
        return spec2Service;
    }

    public Spec2EffectsApplier getSpec2Applier() {
        return spec2Applier;
    }

    public FactionHook getFactionHook() {
        return factionHook;
    }

    public CrownFlavorService getFlavorService() {
        return flavorService;
    }

    public ItemSystem getItems() {
        return items;
    }

    public ItemsApi getItemsApi() {
        return itemsApi;
    }

    public CombatProfileRegistry getCombatProfiles() {
        return combatProfiles;
    }

    public void setEntityLevelProvider(EntityLevelProvider provider) {
        this.entityLevelProvider = provider;
    }

    public EntityLevelProvider getEntityLevelProvider() {
        return entityLevelProvider;
    }

    public BlueprintHook getBlueprintHook() {
        return blueprintHook;
    }

    public FoliantService getFoliantService() {
        return foliantService;
    }

    public FxService getFx() {
        return fx;
    }

    public InstallationService getInstallations() {
        return installations;
    }

    public InstallToken getInstallToken() {
        return installToken;
    }

    public ResistService getResists() {
        return resists;
    }

    public CombatService getCombat() {
        return combat;
    }

    public ManaSoakedService getManaSoaked() {
        return manaSoaked;
    }

    public ConfigValidator getConfigValidator() {
        return configValidator;
    }

    public AttributeService getAttributes() {
        return attributes;
    }

    public HpAttributeSync getHpSync() {
        return hpSync;
    }

    public PassiveListener getPassives() {
        return passiveListener;
    }

    public WarlockAbilities getWarlockAbilities() {
        return warlockAbilities;
    }

    public PetService getPets() {
        return pets;
    }

    public CCService getCC() {
        return ccService;
    }

    public CastGuard getCastGuard() {
        return castGuard;
    }

    public VanillaCCWrapper getVanillaCCWrapper() {
        return vanillaCCWrapper;
    }

    public BuildTemplates getBuildTemplates() {
        return buildTemplates;
    }

    public ImperialWallet getImperialWallet() {
        return imperialWallet;
    }

    public PlusSubscription getPlusSubscription() {
        return plusSubscription;
    }

    public PlusGui getPlusGui() {
        return plusGui;
    }
}

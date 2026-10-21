// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes;

import dev.raskol.classes.ability.AbilityRegistry;
import dev.raskol.classes.ability.CooldownManager;
import dev.raskol.classes.ability.TreeAbilities;
import dev.raskol.classes.ability.WarlockAbilities;
import dev.raskol.classes.ability.passive.PassiveListener;
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
import dev.raskol.classes.hook.BlueprintHook;
import dev.raskol.classes.hook.FactionHook;
import dev.raskol.classes.hook.FlavorPlaceholder;
import dev.raskol.classes.hook.GearHook;
import dev.raskol.classes.hook.PassportChangeListener;
import dev.raskol.classes.hook.SetBonusService;
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
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * RaskolClasses — «РАСКОЛ | ДВЕ КОРОНЫ». Точка сборки сервиса: конструирование
 * слоёв, регистрация слушателей, владение периодическими задачами и геттеры-локаторы.
 * 1.13.0: CC-слой (CCService/CCGuard/CastGuard).
 * 1.14.0 (Б8.2): legacy-слой (talent/*, spec/Spec*, specs.yml) УДАЛЁН;
 *         единственная система спеков/талантов — spec2.
 * 1.14.0 (контент-долг 1): TreeAbilities.registerCasters — древесные способности Воина.
 * 1.14.1 (Волна 1): hpBarService.saveAll() в onDisable — здоровье не теряется при краше.
 * 1.14.4 (Волна 4, П9): Spec2Points.configure(this) в onEnable ДО инициализации
 *         spec2-слоя — очки/гейты рядов читаются из config.yml.
 * 1.14.6 (Волна 6a): PetService — единое ядро боевых петов (wolf/demon/shadowfiend).
 * 1.14.7 (Спринт 2, P1-4): Spec2Points.configure(this) также в reloadPlugin().
 * 1.14.7 (Спринт 2, P0-4): startup-warning для деревьев ниже tree-capacity-min.
 * 1.14.7 (Спринт 4, P1-3): onQuit чистит GUI-сессию книги (TalentsTab.clearSession).
 * 1.14.8 (Спринт 6, Волна 6.2, P0-8-01): ConfigMigrator.migrate148(this) в начале onEnable.
 * 1.14.8 (Спринт 6, Волна 6.3, P0-6-02): purge-задача чистит карты китов и боевые логи.
 * 1.14.10 (Sprint 7, Волна 7.2, P0-4-01): ДЕКОМПОЗИЦИЯ God Object'а (аудит 4-01):
 *         startup-проверки и баннер → bootstrap.StartupChecks; периодические задачи →
 *         bootstrap.PluginTasks; анонимные слушатели входа/выхода → bootstrap.SessionListener.
 * 1.14.12 (Sprint 8, Волна 12A, P0-5-05): refreshDisabledCache() вызывается в onEnable
 *         после создания spec2-слоя и в reloadPlugin() — снапшот стоп-листа актуален
 *         на старте и после /rc reload.
 * 1.14.13 (Sprint 9, Волна 13B, P0-10-03/04): побочные эффекты конструкторов PetService
 *         и FoliantService перенесены сюда: registerListeners()/startTickTask()/
 *         scheduleOrphanScan() и startLockFlushTask() вызываются в onEnable,
 *         ручки задач складываются в activeTasks и отменяются в onDisable явно.
 * 1.14.13 (Sprint 9, Волна 13B-b, P0-10-03/04): побочные эффекты конструкторов
 *         DotService/CCService/CCGuard/VanillaCCWrapper перенесены сюда; ВАЖНО —
 *         экземпляр VanillaCCWrapper раньше не создавался, слушатель EntityPotionEffectEvent
 *         был мёртв (ванильные CC-эффекты не оборачивались в CCService). Теперь создаётся
 *         и регистрируется явно.
 * 1.14.14 (фаза 1, ST_WRITE-фикс): WarlockAbilities создаётся через статическую фабрику
 *         WarlockAbilities.install(this) вместо new WarlockAbilities(this). Это исключает
 *         запись в static-поле из instance-конструктора (SpotBugs ST_WRITE_TO_STATIC).
 * 1.14.15 (фаза 1, raskolspec+): слой подписки на кураторские билды — ImperialWallet
 *         (PlayerPoints по reflection, NullWallet-фолбэк), PlusStorage (plus-subscription.yml),
 *         BuildTemplates (builds.yml), PlusSubscription (grant/revoke/gift/active).
 *         Извлечение builds.yml из jar при первом старте, reload перечитывает на /rc reload.
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

    private GearHook gearHook;
    private SetBonusService setBonusService;
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

    /** 1.14.6 (6a): пет-слой. */
    private PetService pets;

    private CCService ccService;
    private CCGuard ccGuard;
    private CastGuard castGuard;
    /** 1.14.13 (Волна 13B-b): обёртка ванильных CC — раньше не создавался. */
    private VanillaCCWrapper vanillaCCWrapper;

    /** 1.14.15: raskolspec+ — подписка на кураторские билды. */
    private BuildTemplates buildTemplates;
    private ImperialWallet imperialWallet;
    private PlusSubscription plusSubscription;

    private volatile long lastPurgeMillis = System.currentTimeMillis();
    private final long enabledAtMillis = System.currentTimeMillis();

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public void onEnable() {
        this.raskolConfig = new RaskolConfig(this);

        // 1.14.8 (Волна 6.2, P0-8-01): миграция конфига до чтения ключей сервисами
        ConfigMigrator.migrate148(this);

        getLogger().info(() -> "Paper: " + getServer().getVersion()
                + " / Bukkit: " + getServer().getBukkitVersion());

        // 1.14.10 (Волна 7.2): startup-проверки вынесены в bootstrap.StartupChecks
        StartupChecks.logCoreVersion(this);
        StartupChecks.cleanupTmpFiles(this);

        PluginManager pluginManager = getServer().getPluginManager();

        this.classProvider = new ClassProvider(this, raskolConfig);
        if (!classProvider.isAvailable()) {
            getLogger().warning("LuckPerms не найден — определение классов отключено");
        }

        this.gearHook = new GearHook(this);
        pluginManager.registerEvents(gearHook, this);
        gearHook.logStartup();

        this.setBonusService = new SetBonusService(this);
        pluginManager.registerEvents(setBonusService, this);

        this.blueprintHook = new BlueprintHook(this);
        blueprintHook.registerBlueprints();

        this.skillLevels = new SkillLevelProvider(this);
        this.characterLevels = new CharacterLevelService(this);
        this.resources = new ResourceService(this, raskolConfig, classProvider);

        classProvider.setResourceService(resources);

        this.cooldowns = new CooldownManager(new File(getDataFolder(), "cooldowns.yml"));
        this.cooldowns.attachScheduler(this,
                raskolConfig.isReadyNotifyEnabled(),
                raskolConfig.readyNotifyMinCooldownSeconds(),
                raskolConfig.readyNotifySoundKey(),
                raskolConfig.readyNotifyMessage());
        this.effects = new ActiveEffectManager();

        this.abilities = new AbilityRegistry(this);
        abilities.loadFromConfig(raskolConfig);
        // 1.14.0 (контент-долг 1): кастеры древесных способностей Воина
        TreeAbilities.registerCasters(this, abilities);

        this.hud = new HudService(this);
        this.bossBars = new BossBarService(this);
        this.tokens = new AbilityToken(this);

        this.fx = new FxService(this);
        fx.validateConfig();

        this.resists = new ResistService(this);
        this.combat = new CombatService(this, resists);
        // 1.14.13 (Волна 13B-b): DotService создаётся в конструкторе CombatService
        // (чистая ссылка); регистрируем и запускаем тик отсюда.
        combat.dots().registerListeners();
        activeTasks.add(combat.dots().startTickTask());
        this.manaSoaked = new ManaSoakedService(this);

        // 1.14.13 (Волна 13B-b): CCService — конструктор чистый, регистрируем
        // слушатели и запускаем тик-задачу (каждый тик, критична для STUN/ROOT/FEAR)
        this.ccService = new CCService(this);
        ccService.registerListeners();
        activeTasks.add(ccService.startTickTask());

        // 1.14.13 (Волна 13B-b): CCGuard — конструктор чистый, регистрируем явно
        this.ccGuard = new CCGuard(this);
        ccGuard.registerListeners();

        this.castGuard = new CastGuard(this);

        // 1.14.13 (Волна 13B-b): VanillaCCWrapper — ВАЖНО: ранее экземпляр НЕ создавался,
        // слушатель EntityPotionEffectEvent был мёртв. Создаём и регистрируем — теперь
        // ванильные SLOWNESS/BLINDNESS/WEAKNESS (зелья/стрелы) оборачиваются в CCService.
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

        // 1.14.4 (Волна 4, П9): конфиг-ключи spec2.* ДО инициализации spec2-слоя
        Spec2Points.configure(this);

        // 1.14.0: spec2 — единственная система спеков/талантов
        this.spec2Storage = new Spec2Storage(this);
        this.spec2Applier = new Spec2EffectsApplier(this);
        this.spec2Service = new Spec2Service(this, spec2Storage);
        // 1.14.12 (Волна 12A, 5-05): первичная сборка снапшота стоп-листа
        this.spec2Service.refreshDisabledCache();
        pluginManager.registerEvents(new Spec2RoleListener(this), this);

        // 1.14.15 (raskolspec+): кошелёк, хранилище, загрузчик шаблонов, подписка.
        // Создаётся ПОСЛЕ spec2-слоя — BuildTemplates.load() читает disabledNodesSnapshot
        // для stale-маркировки шаблонов.
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
        int periodDays = plugin.getConfig().getInt("plus.period-days", 30);
        this.plusSubscription = new PlusSubscription(this, imperialWallet, plusStorage, periodDays);
        pluginManager.registerEvents(plusSubscription, this);

        this.factionHook = new FactionHook(this);
        this.flavorService = new CrownFlavorService(this, factionHook);

        this.installations = new InstallationService(this);
        this.installToken = new InstallToken(this);

        this.foliantService = new FoliantService(this);
        // 1.14.13 (Волна 13B, 10-04): flush-задача foliant-lock.yml из onEnable,
        // ручка — в activeTasks (отменяется в onDisable явно)
        activeTasks.add(foliantService.startLockFlushTask());

        // 1.14.14 (фаза 1, ST_WRITE-фикс): через статическую фабрику install() —
        // запись в static-поле instance больше не происходит в конструкторе
        this.warlockAbilities = WarlockAbilities.install(this);

        // 1.14.6 (6a): пет-ядро
        this.pets = new PetService(this);
        // 1.14.13 (Волна 13B, 10-03/10-04): слушатели и задачи пет-слоя из onEnable;
        // конструктор PetService больше не имеет побочных эффектов
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
        pluginManager.registerEvents(new ScrollSanitizer(this), this);
        pluginManager.registerEvents(hpBarService, this);
        pluginManager.registerEvents(fx, this);
        pluginManager.registerEvents(foliantService, this);
        pluginManager.registerEvents(warlockAbilities, this);

        this.passportListener = new PassportChangeListener(this);
        passportListener.register();

        // 1.14.10 (Волна 7.2): анонимные слушатели заменены именованным классом
        SessionListener sessions = new SessionListener(this);
        pluginManager.registerEvents(sessions, this);
        sessions.resyncOnline();

        if (pluginManager.getPlugin("PlaceholderAPI") != null) {
            new dev.raskol.classes.hook.RaskolPlaceholder(this).register();
            new FlavorPlaceholder(this).register();
            getLogger().info("PlaceholderAPI: плейсхолдеры %raskolclasses_*% "
                    + "и %raskolcrown_*% зарегистрированы");
        } else {
            getLogger().warning("PlaceholderAPI не найден: плейсхолдеры не регистрируются");
        }

        boolean hudEnabled = getConfig().getBoolean("hud.enabled", true);
        boolean hpVanilla = "vanilla".equalsIgnoreCase(
                getConfig().getString("hp-display.mode", "actionbar"));
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
        // 1.14.10 (Волна 7.2): периодические задачи вынесены в bootstrap.PluginTasks
        activeTasks.add(PluginTasks.startPurgeTask(this));
        activeTasks.add(PluginTasks.startAutosaveTask(this));

        registerCommand();
        StartupChecks.printBanner(this);
        StartupChecks.warnLowCapacityTrees(this);

        getLogger().info(() -> "RaskolClasses v" + getPluginMeta().getVersion() + " запущен");
    }

    /** 1.14.10 (Волна 7.2): отметка времени последнего purge-цикла (для /rc health). */
    public void markPurgeNow() {
        lastPurgeMillis = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        if (hpSync != null) {
            hpSync.stopSweep();
        }
        if (blueprintHook != null) {
            blueprintHook.unregisterBlueprints();
        }
        WarlockAbilities.cancelAllChannelTasks();
        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
        if (passportListener != null) {
            passportListener.unregister();
        }
        if (resources != null) {
            resources.saveAll();
        }
        // 1.14.1 (Волна 1): сохранение здоровья перед выгрузкой
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

    /**
     * 1.14.7 (Спринт 2, P1-4): Spec2Points.configure(this) — snapshot очков/гейтов
     * обновляется при /rc reload без рестарта сервера.
     * 1.14.12 (Волна 12A, 5-05): снапшот стоп-листа пересобирается здесь же.
     * 1.14.15 (raskolspec+): перезагрузка builds.yml.
     */
    public void reloadPlugin() {
        raskolConfig.reload();
        // 1.14.7 (P1-4): snapshot очков/гейтов обновляется при reload
        Spec2Points.configure(this);
        // 1.14.12 (Волна 12A, 5-05): снапшот стоп-листа пересобирается на reload
        if (spec2Service != null) {
            spec2Service.refreshDisabledCache();
        }
        // 1.14.15 (raskolspec+): перечитать builds.yml после обновления стоп-листа
        if (buildTemplates != null) {
            buildTemplates.reload();
        }
        raskolConfig.reloadKits();
        abilities.loadFromConfig(raskolConfig);
        hud.applyConfig();
        cooldowns.attachScheduler(this,
                raskolConfig.isReadyNotifyEnabled(),
                raskolConfig.readyNotifyMinCooldownSeconds(),
                raskolConfig.readyNotifySoundKey(),
                raskolConfig.readyNotifyMessage());
        if (bossBars != null) {
            bossBars.applyConfig();
        }
        fx.validateConfig();
        configValidator.validate();
        for (org.bukkit.entity.Player online : getServer().getOnlinePlayers()) {
            spec2Service.reconcile(online.getUniqueId());
            if (gearHook != null) {
                gearHook.refresh(online);
            }
        }
        if (hpSync != null) {
            hpSync.syncAll();
        }
        getLogger().info("Конфигурация перезагружена");
    }

    public long getLastPurgeMillis() {
        return lastPurgeMillis;
    }

    public long getEnabledAtMillis() {
        return enabledAtMillis;
    }

    public RaskolConfig getRaskolConfig() { return raskolConfig; }
    public ClassProvider getClassProvider() { return classProvider; }
    public SkillLevelProvider getSkillLevels() { return skillLevels; }
    public CharacterLevelService getCharacterLevels() { return characterLevels; }
    public ResourceService getResources() { return resources; }
    public CooldownManager getCooldowns() { return cooldowns; }
    public AbilityRegistry getAbilities() { return abilities; }
    public AbilityRegistry getAbilityRegistry() { return abilities; }
    public ActiveEffectManager getEffects() { return effects; }
    public HudService getHud() { return hud; }
    public BossBarService getBossBars() { return bossBars; }
    public HpBarService getHpBarService() { return hpBarService; }
    public AbilityToken getTokens() { return tokens; }
    /** 1.14.0: spec2-слой. */
    public Spec2Storage getSpec2Storage() { return spec2Storage; }
    public Spec2Service getSpec2Service() { return spec2Service; }
    public Spec2EffectsApplier getSpec2Applier() { return spec2Applier; }
    public FactionHook getFactionHook() { return factionHook; }
    public CrownFlavorService getFlavorService() { return flavorService; }
    public GearHook getGearHook() { return gearHook; }
    public SetBonusService getSetBonusService() { return setBonusService; }
    public BlueprintHook getBlueprintHook() { return blueprintHook; }
    public FoliantService getFoliantService() { return foliantService; }
    public FxService getFx() { return fx; }
    public InstallationService getInstallations() { return installations; }
    public InstallToken getInstallToken() { return installToken; }
    public ResistService getResists() { return resists; }
    public CombatService getCombat() { return combat; }
    public ManaSoakedService getManaSoaked() { return manaSoaked; }
    public ConfigValidator getConfigValidator() { return configValidator; }
    public AttributeService getAttributes() { return attributes; }
    public HpAttributeSync getHpSync() { return hpSync; }
    public PassiveListener getPassives() { return passiveListener; }
    public WarlockAbilities getWarlockAbilities() { return warlockAbilities; }
    /** 1.14.6 (6a): пет-слой. */
    public PetService getPets() { return pets; }
    public CCService getCC() { return ccService; }
    public CastGuard getCastGuard() { return castGuard; }
    /** 1.14.13 (Волна 13B-b): обёртка ванильных CC-эффектов (SLOWNESS/BLINDNESS/WEAKNESS). */
    public VanillaCCWrapper getVanillaCCWrapper() { return vanillaCCWrapper; }
    /** 1.14.15: raskolspec+ — шаблоны. */
    public BuildTemplates getBuildTemplates() { return buildTemplates; }
    /** 1.14.15: raskolspec+ — кошелёк империалов. */
    public ImperialWallet getImperialWallet() { return imperialWallet; }
    /** 1.14.15: raskolspec+ — подписка. */
    public PlusSubscription getPlusSubscription() { return plusSubscription; }
}

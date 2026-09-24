// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes;

import dev.raskol.classes.ability.AbilityRegistry;
import dev.raskol.classes.ability.CooldownManager;
import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.attribute.HpAttributeSync;
import dev.raskol.classes.classsystem.CharacterLevelService;
import dev.raskol.classes.classsystem.ClassProvider;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.combat.CombatService;
import dev.raskol.classes.combat.ManaSoakedService;
import dev.raskol.classes.combat.ResistService;
import dev.raskol.classes.command.RaskolCommand;
import dev.raskol.classes.config.ConfigValidator;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.effect.ActiveEffectManager;
import dev.raskol.classes.flavor.CrownFlavorService;
import dev.raskol.classes.fx.FxService;
import dev.raskol.classes.fx.TrailListener;
import dev.raskol.classes.gui.ClassBook;
import dev.raskol.classes.hook.EconomyHook;
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
import dev.raskol.classes.passive.PassiveListener;
import dev.raskol.classes.resource.ResourceService;
import dev.raskol.classes.spec.SpecEffects;
import dev.raskol.classes.spec.SpecListener;
import dev.raskol.classes.spec.SpecRegistry;
import dev.raskol.classes.spec.SpecService;
import dev.raskol.classes.spec.SpecStorage;
import dev.raskol.classes.spec.SpecToken;
import dev.raskol.classes.talent.TalentService;
import dev.raskol.classes.talent.TalentsStorage;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * RaskolClasses — «РАСКОЛ | ДВЕ КОРОНЫ».
 * 1.9.0: TalentsStorage + TalentService; reconcile талантов на join и /rc reload.
 * 1.9.2: интеграция с RaskolCore 1.3.0 — PassportChangeListener, EconomyHook через Core.
 * 1.9.3: HpAttributeSync (синхронизация ванильного MAX_HEALTH с формулой HP),
 *        полная формула HP, crash-guard китов, мрачный стартовый баннер.
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

    private SpecRegistry specRegistry;
    private SpecStorage specStorage;
    private SpecService specService;
    private SpecEffects specEffects;
    private SpecToken specToken;

    private TalentsStorage talentsStorage;
    private TalentService talentService;

    private FactionHook factionHook;
    private CrownFlavorService flavorService;

    private FxService fx;

    private InstallationService installations;
    private InstallToken installToken;

    private ResistService resists;
    private CombatService combat;
    private ManaSoakedService manaSoaked;
    private ConfigValidator configValidator;
    private AttributeService attributes;

    /** 1.9.3: синхронизация ванильного MAX_HEALTH с формулой HP. */
    private HpAttributeSync hpSync;

    /** 1.9.2: слушатель смены паспорта из RaskolCore. */
    private PassportChangeListener passportListener;

    private volatile long lastPurgeMillis = System.currentTimeMillis();
    private final long enabledAtMillis = System.currentTimeMillis();

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public void onEnable() {
        this.raskolConfig = new RaskolConfig(this);

        getLogger().info(() -> "Paper: " + getServer().getVersion()
                + " / Bukkit: " + getServer().getBukkitVersion());

        checkCoreVersion();

        PluginManager pluginManager = getServer().getPluginManager();

        this.classProvider = new ClassProvider(this, raskolConfig);
        if (!classProvider.isAvailable()) {
            getLogger().warning("LuckPerms не найден — определение классов отключено");
        }

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

        this.hud = new HudService(this);
        this.bossBars = new BossBarService(this);
        this.tokens = new AbilityToken(this);

        this.fx = new FxService(this);
        fx.validateConfig();

        this.resists = new ResistService(this);
        this.combat = new CombatService(this, resists);
        this.manaSoaked = new ManaSoakedService(this);

        this.configValidator = new ConfigValidator(this);
        configValidator.validate();
        configValidator.logSummary();

        this.attributes = new AttributeService(this);
        // 1.9.3: синхронизация ванильного MAX_HEALTH с формулой HP
        this.hpSync = new HpAttributeSync(this);
        pluginManager.registerEvents(hpSync, this);
        hpSync.startSweep(100L);
        this.hpBarService = new HpBarService(this);

        this.specRegistry = new SpecRegistry(this);
        specRegistry.load();
        this.specStorage = new SpecStorage(this);
        specStorage.load();
        this.specEffects = new SpecEffects(this);
        this.specService = new SpecService(this, specStorage, specRegistry);
        this.specToken = new SpecToken(this);

        this.talentsStorage = new TalentsStorage(this);
        this.talentService = new TalentService(this, talentsStorage);

        this.factionHook = new FactionHook(this);
        this.flavorService = new CrownFlavorService(this, factionHook);

        this.installations = new InstallationService(this);
        this.installToken = new InstallToken(this);

        pluginManager.registerEvents(resources, this);
        pluginManager.registerEvents(effects, this);
        pluginManager.registerEvents(cooldowns, this);
        pluginManager.registerEvents(new PassiveListener(this), this);
        pluginManager.registerEvents(new ClassBook.ClickHandler(this), this);
        pluginManager.registerEvents(new BindListener(this, tokens), this);
        pluginManager.registerEvents(new SpecListener(this), this);
        pluginManager.registerEvents(flavorService, this);
        pluginManager.registerEvents(new TrailListener(this), this);
        pluginManager.registerEvents(new InstallBindListener(this, installToken), this);
        pluginManager.registerEvents(combat, this);
        pluginManager.registerEvents(new ScrollSanitizer(this), this);
        pluginManager.registerEvents(hpBarService, this);
        pluginManager.registerEvents(fx, this);

        this.passportListener = new PassportChangeListener(this);
        passportListener.register();

        pluginManager.registerEvents(new Listener() {
            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                resists.clear(event.getPlayer().getUniqueId());
                attributes.clear(event.getPlayer().getUniqueId());
                characterLevels.invalidate(event.getPlayer().getUniqueId());
                talentService.clear(event.getPlayer().getUniqueId());
            }
        }, this);
        pluginManager.registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                specService.restorePassiveResists(event.getPlayer());
                talentService.reconcile(event.getPlayer().getUniqueId());
                hpSync.sync(event.getPlayer());   // 1.9.3: синхронизация HP на join
            }
        }, this);
        for (org.bukkit.entity.Player online : getServer().getOnlinePlayers()) {
            specService.restorePassiveResists(online);
            talentService.reconcile(online.getUniqueId());
            hpSync.sync(online);
        }

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
        int purgeInterval = raskolConfig.purgeIntervalTicks();
        activeTasks.add(getServer().getScheduler().runTaskTimer(this, () -> {
            cooldowns.purgeExpired();
            effects.purgeExpired();
            specEffects.purgeExpired();
            abilities.purgeStaleAttempts();
            fx.purgeStale();
            installations.purgeStale();
            resists.purgeExpired();
            attributes.purgeExpired();
            characterLevels.purgeStale();
            lastPurgeMillis = System.currentTimeMillis();
        }, purgeInterval, purgeInterval));
        long autosaveTicks = Math.max(1, getConfig().getInt("storage.autosave-minutes", 5)) * 60L * 20L;
        activeTasks.add(getServer().getScheduler().runTaskTimer(this, () -> {
            cooldowns.saveAll();
            specStorage.save();
            resources.saveAll();
            talentsStorage.save();
        }, autosaveTicks, autosaveTicks));

        registerCommand();
        printBanner();
        getLogger().info(() -> "RaskolClasses v" + getPluginMeta().getVersion() + " запущен");
    }

    /**
     * 1.9.3: мрачный стартовый баннер в консоль (стиль SiegeWar, тёмная палитра).
     * Градиент арта: серый → тёмно-красный → тёмно-фиолетовый.
     * Снизу — пять классов, автор и версия.
     */
    private void printBanner() {
        String v = getPluginMeta().getVersion();
        String[] art = {
            "&8  ██████╗██╗     █████╗ ███████╗███████╗███████╗",
            "&8  ██╔════╝██║    ██╔══██╗██╔════╝██╔════╝██╔════╝",
            "&4  ██║     ██║    ███████║███████╗█████╗  ███████╗",
            "&4  ██║     ██║    ██╔══██║╚════██║██╔══╝  ╚════██║",
            "&5  ╚██████╗███████╗██║  ██║███████║███████╗███████║",
            "&5   ╚═════╝╚══════╝╚═╝  ╚═╝══════╝╚══════╝╚══════╝",
            "&8  ────────────────────────────────────────────────────",
            "&7     RASKOL &8· &7CLASSES    &8|    &5пять путей &8· &4одна война",
            "&8     ⚔ &4Воин &8· &2➳ Охотник &8· &f✚ Жрец &8· &9✦ Маг &8· &5☠ Разбойник",
            "&8  ────────────────────────────────────────────────────",
            "&8     by &fhayferdahmer &8· &7v" + v + " &8· &7Paper 1.21+ &8· &7Java 21",
            "&8  ────────────────────────────────────────────────────"
        };
        var console = getServer().getConsoleSender();
        for (String line : art) {
            console.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
    }

    /**
     * 1.9.2: стартовая проверка версии RaskolCore (warn-only).
     */
    private void checkCoreVersion() {
        try {
            Class<?> api = Class.forName("dev.raskol.core.RaskolCoreAPI");
            Object versionObj = api.getMethod("coreVersion").invoke(null);
            String version = versionObj == null ? "" : versionObj.toString();
            if (version.isEmpty()) {
                getLogger().warning("RaskolCore: плагин не включён или версия недоступна — "
                        + "мгновенный reconcile на смену паспорта может не работать");
            } else {
                getLogger().info("RaskolCore: версия " + version);
                if ("1.0.0".equals(version) || "1.1.0".equals(version) || "1.2.0".equals(version)) {
                    getLogger().warning("RaskolCore: версия " + version + " устарела — "
                            + "рекомендуется обновить до 1.3.0 для мгновенного reconcile");
                }
            }
        } catch (Throwable t) {
            getLogger().info("RaskolCore: не найден — плагин работает в автономном режиме "
                    + "(мгновенный reconcile на смену паспорта отключён)");
        }
    }

    @Override
    public void onDisable() {
        if (hpSync != null) {
            hpSync.stopSweep();
        }
        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
        if (passportListener != null) {
            passportListener.unregister();
        }
        if (resources != null) {
            resources.saveAll();
        }
        if (talentsStorage != null) {
            talentsStorage.save();
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
        if (specStorage != null) {
            specStorage.save();
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

    public void reloadPlugin() {
        raskolConfig.reload();
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
        if (specRegistry != null) {
            specRegistry.load();
        }
        fx.validateConfig();
        configValidator.validate();
        for (org.bukkit.entity.Player online : getServer().getOnlinePlayers()) {
            talentService.reconcile(online.getUniqueId());
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
    public ActiveEffectManager getEffects() { return effects; }
    public HudService getHud() { return hud; }
    public BossBarService getBossBars() { return bossBars; }
    public HpBarService getHpBarService() { return hpBarService; }
    public AbilityToken getTokens() { return tokens; }
    public SpecRegistry getSpecRegistry() { return specRegistry; }
    public SpecStorage getSpecStorage() { return specStorage; }
    public SpecService getSpecService() { return specService; }
    public SpecEffects getSpecEffects() { return specEffects; }
    public SpecToken getSpecToken() { return specToken; }
    public TalentsStorage getTalentsStorage() { return talentsStorage; }
    public TalentService getTalentService() { return talentService; }
    public FactionHook getFactionHook() { return factionHook; }
    public CrownFlavorService getFlavorService() { return flavorService; }
    public FxService getFx() { return fx; }
    public InstallationService getInstallations() { return installations; }
    public InstallToken getInstallToken() { return installToken; }
    public ResistService getResists() { return resists; }
    public CombatService getCombat() { return combat; }
    public ManaSoakedService getManaSoaked() { return manaSoaked; }
    public ConfigValidator getConfigValidator() { return configValidator; }
    public AttributeService getAttributes() { return attributes; }
    public HpAttributeSync getHpSync() { return hpSync; }
}

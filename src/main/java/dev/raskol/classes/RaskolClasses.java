// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes;

import dev.raskol.classes.ability.AbilityRegistry;
import dev.raskol.classes.ability.CooldownManager;
import dev.raskol.classes.classsystem.ClassProvider;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.command.RaskolCommand;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.effect.ActiveEffectManager;
import dev.raskol.classes.flavor.CrownFlavorService;
import dev.raskol.classes.fx.FxService;
import dev.raskol.classes.fx.TrailListener;
import dev.raskol.classes.gui.ClassMenu;
import dev.raskol.classes.hotbar.AbilityToken;
import dev.raskol.classes.hotbar.BindListener;
import dev.raskol.classes.hotbar.SpecBindListener;
import dev.raskol.classes.hotbar.SpecToken;
import dev.raskol.classes.hook.FactionHook;
import dev.raskol.classes.hook.FlavorPlaceholder;
import dev.raskol.classes.hud.BossBarService;
import dev.raskol.classes.hud.HudService;
import dev.raskol.classes.install.InstallBindListener;
import dev.raskol.classes.install.InstallToken;
import dev.raskol.classes.install.InstallationService;
import dev.raskol.classes.passive.PassiveListener;
import dev.raskol.classes.resource.ResourceService;
import dev.raskol.classes.spec.SpecActiveCaster;
import dev.raskol.classes.spec.SpecEffects;
import dev.raskol.classes.spec.SpecListener;
import dev.raskol.classes.spec.SpecMenu;
import dev.raskol.classes.spec.SpecRegistry;
import dev.raskol.classes.spec.SpecService;
import dev.raskol.classes.spec.SpecStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * RaskolClasses — «РАСКОЛ | ДВЕ КОРОНЫ».
 * 1.5.1: guard спеки против смены класса, ready-notify спек-абилок,
 * чистка памяти, гигиена респеца, таргет-каст без waste на creative/spectator.
 */
public final class RaskolClasses extends JavaPlugin {

    private RaskolConfig raskolConfig;
    private ClassProvider classProvider;
    private SkillLevelProvider skillLevels;
    private ResourceService resources;
    private CooldownManager cooldowns;
    private AbilityRegistry abilities;
    private ActiveEffectManager effects;
    private HudService hud;
    private BossBarService bossBars;
    private AbilityToken tokens;

    private SpecRegistry specRegistry;
    private SpecStorage specStorage;
    private SpecService specService;
    private SpecEffects specEffects;
    private SpecActiveCaster specCaster;
    private SpecToken specToken;

    private FactionHook factionHook;
    private CrownFlavorService flavorService;

    private FxService fx;

    private InstallationService installations;
    private InstallToken installToken;

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public void onEnable() {
        this.raskolConfig = new RaskolConfig(this);

        getLogger().info(() -> "Paper: " + getServer().getVersion()
                + " / Bukkit: " + getServer().getBukkitVersion());

        PluginManager pluginManager = getServer().getPluginManager();

        this.classProvider = new ClassProvider(this, raskolConfig);
        if (!classProvider.isAvailable()) {
            getLogger().warning("LuckPerms не найден — определение классов отключено");
        }

        this.skillLevels = new SkillLevelProvider(this);
        this.resources = new ResourceService(raskolConfig, classProvider);

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

        this.specRegistry = new SpecRegistry(this);
        specRegistry.load();
        this.specStorage = new SpecStorage(this);
        specStorage.load();
        this.specEffects = new SpecEffects(this);
        this.specService = new SpecService(this, specStorage, specRegistry);
        this.specCaster = new SpecActiveCaster(this);
        this.specToken = new SpecToken(this);

        this.factionHook = new FactionHook(this);
        this.flavorService = new CrownFlavorService(this, factionHook);

        this.installations = new InstallationService(this);
        this.installToken = new InstallToken(this);

        pluginManager.registerEvents(resources, this);
        pluginManager.registerEvents(effects, this);
        pluginManager.registerEvents(cooldowns, this);
        pluginManager.registerEvents(new PassiveListener(this), this);
        pluginManager.registerEvents(new ClassMenu.ClickHandler(this), this);
        pluginManager.registerEvents(new BindListener(this, tokens), this);
        pluginManager.registerEvents(new SpecBindListener(this, specToken), this);
        pluginManager.registerEvents(new SpecListener(this), this);
        pluginManager.registerEvents(new SpecMenu.ClickHandler(this), this);
        pluginManager.registerEvents(flavorService, this);
        pluginManager.registerEvents(new TrailListener(this), this);
        pluginManager.registerEvents(new InstallBindListener(this, installToken), this);

        if (pluginManager.getPlugin("PlaceholderAPI") != null) {
            new dev.raskol.classes.hook.RaskolPlaceholder(this).register();
            new FlavorPlaceholder(this).register();
            getLogger().info("PlaceholderAPI: плейсхолдеры %raskolclasses_*% "
                    + "и %raskolcrown_*% зарегистрированы");
        } else {
            getLogger().warning("PlaceholderAPI не найден: плейсхолдеры не регистрируются");
        }

        activeTasks.add(hud.start());
        activeTasks.add(resources.startTickTask(this));
        activeTasks.add(bossBars.start());
        activeTasks.add(flavorService.startAuraTask());
        activeTasks.add(installations.startSweepTask());
        // 1.5.1: ready-notify спек-абилок
        activeTasks.add(specService.startSpecNotifyTask());
        int purgeInterval = raskolConfig.purgeIntervalTicks();
        activeTasks.add(getServer().getScheduler().runTaskTimer(this, () -> {
            cooldowns.purgeExpired();
            effects.purgeExpired();
            specEffects.purgeExpired();
            // 1.5.1: чистка анти-спам карты
            abilities.purgeStaleAttempts();
        }, purgeInterval, purgeInterval));

        registerCommand();
        getLogger().info(() -> "RaskolClasses v" + getPluginMeta().getVersion() + " запущен");
    }

    @Override
    public void onDisable() {
        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
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
        getLogger().info("Конфигурация перезагружена");
    }

    public RaskolConfig getRaskolConfig() { return raskolConfig; }
    public ClassProvider getClassProvider() { return classProvider; }
    public SkillLevelProvider getSkillLevels() { return skillLevels; }
    public ResourceService getResources() { return resources; }
    public CooldownManager getCooldowns() { return cooldowns; }
    public AbilityRegistry getAbilities() { return abilities; }
    public ActiveEffectManager getEffects() { return effects; }
    public HudService getHud() { return hud; }
    public BossBarService getBossBars() { return bossBars; }
    public AbilityToken getTokens() { return tokens; }
    public SpecRegistry getSpecRegistry() { return specRegistry; }
    public SpecStorage getSpecStorage() { return specStorage; }
    public SpecService getSpecService() { return specService; }
    public SpecEffects getSpecEffects() { return specEffects; }
    public SpecActiveCaster getSpecCaster() { return specCaster; }
    public SpecToken getSpecToken() { return specToken; }
    public FactionHook getFactionHook() { return factionHook; }
    public CrownFlavorService getFlavorService() { return flavorService; }
    public FxService getFx() { return fx; }
    public InstallationService getInstallations() { return installations; }
    public InstallToken getInstallToken() { return installToken; }
}

// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes;

import dev.raskol.classes.ability.AbilityRegistry;
import dev.raskol.classes.ability.CooldownManager;
import dev.raskol.classes.attribute.AttributeService;
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
import dev.raskol.classes.hotbar.AbilityToken;
import dev.raskol.classes.hotbar.BindListener;
import dev.raskol.classes.hotbar.ScrollCooldownTask;
import dev.raskol.classes.hotbar.ScrollSanitizer;
import dev.raskol.classes.hook.FactionHook;
import dev.raskol.classes.hook.FlavorPlaceholder;
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
import java.util.Locale;

/**
 * RaskolClasses — «РАСКОЛ | ДВЕ КОРОНЫ».
 * 1.7.5: спеки = пассивная идентичность (SpecBindListener/SpecActiveCaster удалены).
 * 1.8.0: CharacterLevelService — сводный уровень персонажа (топ-N скиллов, кап);
 * атрибуты растут от него (attributes.level-source=character), анлоки абилок
 * остаются на профильном скилле.
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

    private volatile long lastPurgeMillis = System.currentTimeMillis();
    private final long enabledAtMillis = System.currentTimeMillis();

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
        // 1.8.0: сводный уровень персонажа (до AttributeService — levelOf читает его)
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

        // 1.7.0 пакет 1: классовые атрибуты (1.8.0: level-source=character по умолчанию)
        this.attributes = new AttributeService(this);
        // 1.7.0 пакет 2 + 1.7.4.1: HP-бар, применение maxHP, персист здоровья
        this.hpBarService = new HpBarService(this);

        this.specRegistry = new SpecRegistry(this);
        specRegistry.load();
        this.specStorage = new SpecStorage(this);
        specStorage.load();
        this.specEffects = new SpecEffects(this);
        this.specService = new SpecService(this, specStorage, specRegistry);
        this.specToken = new SpecToken(this);

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
        pluginManager.registerEvents(new Listener() {
            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                resists.clear(event.getPlayer().getUniqueId());
                attributes.clear(event.getPlayer().getUniqueId());
                characterLevels.invalidate(event.getPlayer().getUniqueId());
            }
        }, this);
        pluginManager.registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                specService.restorePassiveResists(event.getPlayer());
            }
        }, this);
        for (org.bukkit.entity.Player online : getServer().getOnlinePlayers()) {
            specService.restorePassiveResists(online);
        }

        if (pluginManager.getPlugin("PlaceholderAPI") != null) {
            new dev.raskol.classes.hook.RaskolPlaceholder(this).register();
            new FlavorPlaceholder(this).register();
            getLogger().info("PlaceholderAPI: плейсхолдеры %raskolclasses_*% "
                    + "и %raskolcrown_*% зарегистрированы");
        } else {
            getLogger().warning("PlaceholderAPI не найден: плейсхолдеры не регистрируются");
        }

        // HUD-ресурс только в vanilla-режиме; в actionbar-режиме строку шлёт HpBarService
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
        }, autosaveTicks, autosaveTicks));

        registerCommand();
        getLogger().info(() -> "RaskolClasses v" + getPluginMeta().getVersion() + " запущен");
    }

    @Override
    public void onDisable() {
        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
        if (resources != null) {
            resources.saveAll();
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
}

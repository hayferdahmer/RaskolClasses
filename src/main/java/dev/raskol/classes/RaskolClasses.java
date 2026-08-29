// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes;

import dev.raskol.classes.ability.AbilityRegistry;
import dev.raskol.classes.ability.CooldownManager;
import dev.raskol.classes.classsystem.ClassProvider;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.command.RaskolCommand;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.effect.ActiveEffectManager;
import dev.raskol.classes.gui.ClassMenu;
import dev.raskol.classes.hud.BossBarService;
import dev.raskol.classes.hud.HudService;
import dev.raskol.classes.passive.PassiveListener;
import dev.raskol.classes.resource.ResourceService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * RaskolClasses — активные способности пяти классов сервера «РАСКОЛ | ДВЕ КОРОНЫ».
 * Рантайм: Paper 26.2 · компиляция: paper-api 1.21.4 (нижняя планка, V1) · Java 21.
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
        // Пакет 5: босс-бар V2
        this.bossBars = new BossBarService(this);

        pluginManager.registerEvents(resources, this);
        pluginManager.registerEvents(effects, this);
        pluginManager.registerEvents(cooldowns, this);
        pluginManager.registerEvents(new PassiveListener(this), this);
        pluginManager.registerEvents(new ClassMenu.ClickHandler(this), this);

        if (pluginManager.getPlugin("PlaceholderAPI") != null) {
            new dev.raskol.classes.hook.RaskolPlaceholder(this).register();
            getLogger().info("PlaceholderAPI: плейсхолдеры %raskolclasses_*% зарегистрированы");
        } else {
            getLogger().warning("PlaceholderAPI не найден: плейсхолдеры не регистрируются");
        }

        activeTasks.add(hud.start());
        activeTasks.add(resources.startTickTask(this));
        activeTasks.add(bossBars.start());
        int purgeInterval = raskolConfig.purgeIntervalTicks();
        activeTasks.add(getServer().getScheduler().runTaskTimer(this, () -> {
            cooldowns.purgeExpired();
            effects.purgeExpired();
        }, purgeInterval, purgeInterval));

        registerCommand();
        getLogger().info(() -> "RaskolClasses v" + getPluginMeta().getVersion() + " запущен");
    }

    @Override
    public void onDisable() {
        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
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

    public void reloadPlugin() {
        raskolConfig.reload();
        abilities.loadFromConfig(raskolConfig);
        hud.applyConfig();
        // Пакет 2: ready-notify
        cooldowns.attachScheduler(this,
                raskolConfig.isReadyNotifyEnabled(),
                raskolConfig.readyNotifyMinCooldownSeconds(),
                raskolConfig.readyNotifySoundKey(),
                raskolConfig.readyNotifyMessage());
        // Пакет 5: босс-бар V2
        if (bossBars != null) {
            bossBars.applyConfig();
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
}

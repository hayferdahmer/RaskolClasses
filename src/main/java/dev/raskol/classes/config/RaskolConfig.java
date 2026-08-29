// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.config;

import dev.raskol.classes.classsystem.PlayerClass;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Конфигурация с дефолтами из кода: баланс, темы классов (§4), длительности
 * эффектов (D1), пассивки (§6 пакета 5) и параметры производительности (§2).
 * addDefault + copyDefaults дописывают новые ключи, не затирая правки админа.
 * Русские имена/описания пассивок и описания активок живут в коде как фолбэк —
 * UI корректен даже если старый config.yml не получил новые ключи.
 */
public final class RaskolConfig {

    private final JavaPlugin plugin;
    private final Map<PlayerClass, ClassTheme> themes = new EnumMap<>(PlayerClass.class);

    public RaskolConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public record ClassTheme(TextColor primary, TextColor secondary, String symbol,
                             Particle particle, Sound sound) {
    }

    public final void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        config.addDefault("hud.enabled", true);
        config.addDefault("hud.update-period-ticks", 10);
        config.addDefault("hud.skip-spectator", true);
        config.addDefault("hud.full-resource-aura", true);
        config.addDefault("hud.debug-skip", false);
        config.addDefault("performance.purge-interval-ticks", 1200);
        config.addDefault("performance.cast-click-cooldown-ms", 150L);

        config.addDefault("class-accept.enabled", true);
        config.addDefault("class-accept.subtitle", "Твой путь избран");

        for (PlayerClass pc : PlayerClass.values()) {
            String base = "classes." + pc.name();
            config.addDefault(base + ".resource-regen", DEFAULTS.resourceRegen(pc));
            config.addDefault(base + ".resource-on-deal", DEFAULTS.resourceOnDeal(pc));
            config.addDefault(base + ".resource-on-take", DEFAULTS.resourceOnTake(pc));
            config.addDefault(base + ".resource-on-heal", DEFAULTS.resourceOnHeal(pc));
            config.addDefault(base + ".combat-window-seconds", 5);
            config.addDefault(base + ".theme.primary", DEFAULTS.primary(pc));
            config.addDefault(base + ".theme.secondary", DEFAULTS.secondary(pc));
            config.addDefault(base + ".theme.symbol", DEFAULTS.symbol(pc));
            config.addDefault(base + ".cast-sound", "ENTITY_PLAYER_LEVELUP");
            config.addDefault(base + ".cast-particle", DEFAULTS.particle(pc).name());
            DEFAULTS.abilities(pc).forEach((id, a) -> {
                config.addDefault(base + ".abilities." + id + ".unlock", a.unlock());
                config.addDefault(base + ".abilities." + id + ".cost", a.cost());
                config.addDefault(base + ".abilities." + id + ".cooldown", a.cooldown());
                config.addDefault(base + ".abilities." + id + ".name", a.name());
                config.addDefault(base + ".abilities." + id + ".description", a.description());
                if (a.duration() > 0) {
                    config.addDefault(base + ".abilities." + id + ".duration", a.duration());
                }
            });
        }
        config.addDefault("classes.HUNTER.abilities.cheetah_aspect.duration-speed", 8);
        config.addDefault("classes.HUNTER.abilities.cheetah_aspect.duration-no-fall", 10);

        // Пассивки (§6 пакета 5)
        config.addDefault("classes.WARRIOR.passives.execute_passive.enabled", true);
        config.addDefault("classes.WARRIOR.passives.execute_passive.chance", 0.20);
        config.addDefault("classes.WARRIOR.passives.execute_passive.multiplier", 3.0);
        config.addDefault("classes.WARRIOR.passives.execute_passive.threshold", 0.20);
        config.addDefault("classes.WARRIOR.passives.execute_passive.cooldown-seconds", 6);
        config.addDefault("classes.HUNTER.passives.predator.enabled", true);
        config.addDefault("classes.HUNTER.passives.predator.threshold", 0.80);
        config.addDefault("classes.HUNTER.passives.predator.multiplier", 1.20);
        config.addDefault("classes.PRIEST.passives.grace.enabled", true);
        config.addDefault("classes.PRIEST.passives.grace.multiplier", 1.15);
        config.addDefault("classes.MAGE.passives.mana_soaked.enabled", true);
        config.addDefault("classes.MAGE.passives.mana_soaked.threshold", 50.0);
        config.addDefault("classes.MAGE.passives.mana_soaked.reduction", 0.15);
        config.addDefault("classes.MAGE.passives.mana_soaked.regen-bonus", 1.0);
        config.addDefault("classes.ROGUE.passives.poisoned_blades.enabled", true);
        config.addDefault("classes.ROGUE.passives.poisoned_blades.chance", 0.30);
        config.addDefault("classes.ROGUE.passives.poisoned_blades.duration-seconds", 2);
        config.addDefault("classes.ROGUE.passives.poisoned_blades.cooldown-seconds", 3);
        config.addDefault("classes.ROGUE.passives.sadism.enabled", true);
        config.addDefault("classes.ROGUE.passives.sadism.bonus", 3.0);
        config.addDefault("classes.ROGUE.passives.sadism.cooldown-seconds", 2);

        // Пакет 1: display-name/description пассивок (фолбэк — в коде, см. DEFAULTS)
        config.addDefault("classes.WARRIOR.passives.execute_passive.display-name", "Казнь");
        config.addDefault("classes.WARRIOR.passives.execute_passive.description",
                "20% шанс — ×3 урона по цели с ≤20% HP");
        config.addDefault("classes.HUNTER.passives.predator.display-name", "Хищник");
        config.addDefault("classes.HUNTER.passives.predator.description",
                "×1.2 урона, пока HP ≥ 80%");
        config.addDefault("classes.PRIEST.passives.grace.display-name", "Благодать");
        config.addDefault("classes.PRIEST.passives.grace.description",
                "×1.15 к исходящему лечению");
        config.addDefault("classes.MAGE.passives.mana_soaked.display-name", "Пропитанный маной");
        config.addDefault("classes.MAGE.passives.mana_soaked.description",
                "мана ≥ 50 → −15% входящего урона");
        config.addDefault("classes.ROGUE.passives.poisoned_blades.display-name", "Отравленные клинки");
        config.addDefault("classes.ROGUE.passives.poisoned_blades.description",
                "30% шанс — Яд I на 2 с");
        config.addDefault("classes.ROGUE.passives.sadism.display-name", "Садизм");
        config.addDefault("classes.ROGUE.passives.sadism.description",
                "+3 урона при атаке со спины");

        config.options().copyDefaults(true);
        plugin.saveConfig();
        rebuildThemes();
    }

    /* ------------------------- HUD и производительность ------------------------- */

    public boolean isHudEnabled() {
        return plugin.getConfig().getBoolean("hud.enabled", true);
    }

    public int hudUpdatePeriodTicks() {
        return plugin.getConfig().getInt("hud.update-period-ticks", 10);
    }

    public boolean hudSkipSpectator() {
        return plugin.getConfig().getBoolean("hud.skip-spectator", true);
    }

    public boolean hudFullResourceAura() {
        return plugin.getConfig().getBoolean("hud.full-resource-aura", true);
    }

    public boolean hudDebugSkip() {
        return plugin.getConfig().getBoolean("hud.debug-skip", false);
    }

    public int purgeIntervalTicks() {
        return plugin.getConfig().getInt("performance.purge-interval-ticks", 1200);
    }

    public long castClickCooldownMillis() {
        return plugin.getConfig().getLong("performance.cast-click-cooldown-ms", 150L);
    }

    /* ------------------------- V1: принятие класса ------------------------- */

    public boolean isClassAcceptEnabled() {
        return plugin.getConfig().getBoolean("class-accept.enabled", true);
    }

    public String classAcceptSubtitle() {
        return plugin.getConfig().getString("class-accept.subtitle", "Твой путь избран");
    }

    /* ------------------------- пассивки (§6 пакета 5) ------------------------- */

    public boolean passiveEnabled(PlayerClass pc, String id) {
        return plugin.getConfig().getBoolean(passivePath(pc, id, "enabled"), true);
    }

    public double passiveDouble(PlayerClass pc, String id, String key, double fallback) {
        return plugin.getConfig().getDouble(passivePath(pc, id, key), fallback);
    }

    public int passiveInt(PlayerClass pc, String id, String key, int fallback) {
        return plugin.getConfig().getInt(passivePath(pc, id, key), fallback);
    }

    /** Имя пассивки: конфиг → код-фолбэк (русское имя) → переданный fallback. */
    public String passiveDisplayName(PlayerClass pc, String id, String fallback) {
        return plugin.getConfig().getString(passivePath(pc, id, "display-name"),
                DEFAULTS.passiveDisplayName(pc, id, fallback));
    }

    /** Описание пассивки: конфиг → код-фолбэк → переданный fallback. */
    public String passiveDescription(PlayerClass pc, String id, String fallback) {
        return plugin.getConfig().getString(passivePath(pc, id, "description"),
                DEFAULTS.passiveDescription(pc, id, fallback));
    }

    public static List<String> passiveIds(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> List.of("execute_passive");
            case HUNTER -> List.of("predator");
            case PRIEST -> List.of("grace");
            case MAGE -> List.of("mana_soaked");
            case ROGUE -> List.of("poisoned_blades", "sadism");
        };
    }

    private String passivePath(PlayerClass pc, String id, String key) {
        return "classes." + pc.name() + ".passives." + id + "." + key;
    }

    /* ------------------------- ресурсы ------------------------- */

    public double resourceRegen(PlayerClass pc) {
        return plugin.getConfig().getDouble("classes." + pc.name() + ".resource-regen",
                DEFAULTS.resourceRegen(pc));
    }

    public double resourceOnDeal(PlayerClass pc) {
        return plugin.getConfig().getDouble("classes." + pc.name() + ".resource-on-deal",
                DEFAULTS.resourceOnDeal(pc));
    }

    public double resourceOnTake(PlayerClass pc) {
        return plugin.getConfig().getDouble("classes." + pc.name() + ".resource-on-take",
                DEFAULTS.resourceOnTake(pc));
    }

    public double resourceOnHeal(PlayerClass pc) {
        return plugin.getConfig().getDouble("classes." + pc.name() + ".resource-on-heal",
                DEFAULTS.resourceOnHeal(pc));
    }

    public int combatWindowSeconds(PlayerClass pc) {
        return plugin.getConfig().getInt("classes." + pc.name() + ".combat-window-seconds", 5);
    }

    /* ------------------------- способности ------------------------- */

    public int abilityUnlock(PlayerClass pc, String id, int fallback) {
        return plugin.getConfig().getInt(path(pc, id, "unlock"), fallback);
    }

    public int abilityCost(PlayerClass pc, String id, int fallback) {
        return plugin.getConfig().getInt(path(pc, id, "cost"), fallback);
    }

    public int abilityCooldownSeconds(PlayerClass pc, String id, int fallback) {
        return plugin.getConfig().getInt(path(pc, id, "cooldown"), fallback);
    }

    public String abilityName(PlayerClass pc, String id, String fallback) {
        return plugin.getConfig().getString(path(pc, id, "name"), fallback);
    }

    /** Описание способности: конфиг → код-фолбэк (из DEFAULTS) → переданный fallback. */
    public String abilityDescription(PlayerClass pc, String id, String fallback) {
        AbilityDefaults d = DEFAULTS.abilities(pc).get(id);
        String codeDefault = d != null ? d.description() : "";
        return plugin.getConfig().getString(path(pc, id, "description"),
                codeDefault.isEmpty() ? fallback : codeDefault);
    }

    /* ------------------------- длительности эффектов (D1/D2) ------------------------- */

    public int durationSeconds(PlayerClass pc, String abilityId, int fallbackSeconds) {
        return plugin.getConfig().getInt(path(pc, abilityId, "duration"), fallbackSeconds);
    }

    public int durationSeconds(PlayerClass pc, String abilityId, String subKey, int fallbackSeconds) {
        return plugin.getConfig().getInt(path(pc, abilityId, "duration-" + subKey), fallbackSeconds);
    }

    private String path(PlayerClass pc, String abilityId, String key) {
        return "classes." + pc.name() + ".abilities

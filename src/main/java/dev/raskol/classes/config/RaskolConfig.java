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

        // Пакет 6: хотбар-бинд (свитки способностей)
        config.addDefault("hotbar-bind.enabled", true);
        config.addDefault("hotbar-bind.material", "AMETHYST_SHARD");

        config.addDefault("hud.boss-bar.enabled", true);
        config.addDefault("hud.boss-bar.min-duration-seconds", 8);
        config.addDefault("hud.boss-bar.max-visible", 2);
        config.addDefault("hud.boss-bar.tick-period", 5);
        config.addDefault("hud.boss-bar.format", "{name} — {sec}с");
        config.addDefault("hud.boss-bar.format-infinite", "{name}");

        config.addDefault("performance.purge-interval-ticks", 1200);
        config.addDefault("performance.cast-click-cooldown-ms", 150L);

        config.addDefault("performance.ready-notify.enabled", true);
        config.addDefault("performance.ready-notify.min-cooldown-seconds", 30);
        config.addDefault("performance.ready-notify.sound", "ENTITY_PLAYER_LEVELUP");
        config.addDefault("performance.ready-notify.message", "{ability} — готова");

        config.addDefault("class-accept.enabled", true);
        config.addDefault("class-accept.subtitle", "Твой путь избран");

        config.addDefault("messages.no-class", "Класс не выбран — посетите герольда");
        config.addDefault("messages.no-class-cast", "Класс не выбран — способности недоступны");
        config.addDefault("messages.no-permission", "Недостаточно прав");
        config.addDefault("messages.unlock", "«{ability}» откроется на уровне {required} (у вас {current})");
        config.addDefault("messages.cooldown", "«{ability}»: перезарядка ещё {seconds}с");
        config.addDefault("messages.no-resource", "Не хватает ресурса «{resource}»: нужно {cost}, у вас {value}");
        config.addDefault("messages.activated", "«{ability}» — активирована");
        config.addDefault("messages.blink-unsafe", "Скачок невозможен: нет безопасной точки");
        config.addDefault("messages.cheap-shot-no-target", "Нет цели в радиусе 4 блоков");
        config.addDefault("messages.tag.execute", "Казнь ×3!");
        config.addDefault("messages.tag.predator", "Хищник!");
        config.addDefault("messages.tag.poison", "Яд!");
        config.addDefault("messages.tag.backstab", "В спину +3!");

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

        // Пакет 5b: тиры регена мага
        config.addDefault("classes.MAGE.regen-tier-1", 3.0);
        config.addDefault("classes.MAGE.regen-tier-2", 4.0);
        config.addDefault("classes.MAGE.regen-tier-3", 5.0);
        config.addDefault("classes.MAGE.regen-tier-4", 6.0);

        config.addDefault("classes.HUNTER.abilities.cheetah_aspect.duration-speed", 8);
        config.addDefault("classes.HUNTER.abilities.cheetah_aspect.duration-no-fall", 10);

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

    public boolean isHudEnabled() { return plugin.getConfig().getBoolean("hud.enabled", true); }
    public int hudUpdatePeriodTicks() { return plugin.getConfig().getInt("hud.update-period-ticks", 10); }
    public boolean hudSkipSpectator() { return plugin.getConfig().getBoolean("hud.skip-spectator", true); }
    public boolean hudFullResourceAura() { return plugin.getConfig().getBoolean("hud.full-resource-aura", true); }
    public boolean hudDebugSkip() { return plugin.getConfig().getBoolean("hud.debug-skip", false); }

    // Пакет 6: хотбар-бинд
    public boolean isBindEnabled() { return plugin.getConfig().getBoolean("hotbar-bind.enabled", true); }
    public String bindMaterial() { return plugin.getConfig().getString("hotbar-bind.material", "AMETHYST_SHARD"); }

    public boolean isBossBarEnabled() { return plugin.getConfig().getBoolean("hud.boss-bar.enabled", true); }
    public int bossBarMinDurationSeconds() { return plugin.getConfig().getInt("hud.boss-bar.min-duration-seconds", 8); }
    public int bossBarMaxVisible() { return plugin.getConfig().getInt("hud.boss-bar.max-visible", 2); }
    public int bossBarTickPeriod() { return plugin.getConfig().getInt("hud.boss-bar.tick-period", 5); }
    public String bossBarFormat() { return plugin.getConfig().getString("hud.boss-bar.format", "{name} — {sec}с"); }
    public String bossBarFormatInfinite() { return plugin.getConfig().getString("hud.boss-bar.format-infinite", "{name}"); }

    public int purgeIntervalTicks() { return plugin.getConfig().getInt("performance.purge-interval-ticks", 1200); }
    public long castClickCooldownMillis() { return plugin.getConfig().getLong("performance.cast-click-cooldown-ms", 150L); }

    public boolean isReadyNotifyEnabled() { return plugin.getConfig().getBoolean("performance.ready-notify.enabled", true); }
    public int readyNotifyMinCooldownSeconds() { return plugin.getConfig().getInt("performance.ready-notify.min-cooldown-seconds", 30); }
    public String readyNotifySoundKey() { return plugin.getConfig().getString("performance.ready-notify.sound", "ENTITY_PLAYER_LEVELUP"); }
    public String readyNotifyMessage() { return plugin.getConfig().getString("performance.ready-notify.message", "{ability} — готова"); }

    public boolean isClassAcceptEnabled() { return plugin.getConfig().getBoolean("class-accept.enabled", true); }
    public String classAcceptSubtitle() { return plugin.getConfig().getString("class-accept.subtitle", "Твой путь избран"); }

    public String message(String key, String fallback) {
        String v = plugin.getConfig().getString("messages." + key, null);
        return v != null ? v : fallback;
    }

    // Пакет 5b: тиры регена мага
    public double mageRegenTier1() { return plugin.getConfig().getDouble("classes.MAGE.regen-tier-1", 3.0); }
    public double mageRegenTier2() { return plugin.getConfig().getDouble("classes.MAGE.regen-tier-2", 4.0); }
    public double mageRegenTier3() { return plugin.getConfig().getDouble("classes.MAGE.regen-tier-3", 5.0); }
    public double mageRegenTier4() { return plugin.getConfig().getDouble("classes.MAGE.regen-tier-4", 6.0); }

    public boolean passiveEnabled(PlayerClass pc, String id) {
        return plugin.getConfig().getBoolean(passivePath(pc, id, "enabled"), true);
    }
    public double passiveDouble(PlayerClass pc, String id, String key, double fallback) {
        return plugin.getConfig().getDouble(passivePath(pc, id, key), fallback);
    }
    public int passiveInt(PlayerClass pc, String id, String key, int fallback) {
        return plugin.getConfig().getInt(passivePath(pc, id, key), fallback);
    }
    public String passiveDisplayName(PlayerClass pc, String id, String fallback) {
        return plugin.getConfig().getString(passivePath(pc, id, "display-name"),
                DEFAULTS.passiveDisplayName(pc, id, fallback));
    }
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

    public double resourceRegen(PlayerClass pc) { return plugin.getConfig().getDouble("classes." + pc.name() + ".resource-regen", DEFAULTS.resourceRegen(pc)); }
    public double resourceOnDeal(PlayerClass pc) { return plugin.getConfig().getDouble("classes." + pc.name() + ".resource-on-deal", DEFAULTS.resourceOnDeal(pc)); }
    public double resourceOnTake(PlayerClass pc) { return plugin.getConfig().getDouble("classes." + pc.name() + ".resource-on-take", DEFAULTS.resourceOnTake(pc)); }
    public double resourceOnHeal(PlayerClass pc) { return plugin.getConfig().getDouble("classes." + pc.name() + ".resource-on-heal", DEFAULTS.resourceOnHeal(pc)); }
    public int combatWindowSeconds(PlayerClass pc) { return plugin.getConfig().getInt("classes." + pc.name() + ".combat-window-seconds", 5); }

    public int abilityUnlock(PlayerClass pc, String id, int fallback) { return plugin.getConfig().getInt(path(pc, id, "unlock"), fallback); }
    public int abilityCost(PlayerClass pc, String id, int fallback) { return plugin.getConfig().getInt(path(pc, id, "cost"), fallback); }
    public int abilityCooldownSeconds(PlayerClass pc, String id, int fallback) { return plugin.getConfig().getInt(path(pc, id, "cooldown"), fallback); }
    public String abilityName(PlayerClass pc, String id, String fallback) { return plugin.getConfig().getString(path(pc, id, "name"), fallback); }
    public String abilityDescription(PlayerClass pc, String id, String fallback) {
        AbilityDefaults d = DEFAULTS.abilities(pc).get(id);
        String codeDefault = d != null ? d.description() : "";
        return plugin.getConfig().getString(path(pc, id, "description"),
                codeDefault.isEmpty() ? fallback : codeDefault);
    }

    public int durationSeconds(PlayerClass pc, String abilityId, int fallbackSeconds) {
        return plugin.getConfig().getInt(path(pc, abilityId, "duration"), fallbackSeconds);
    }
    public int durationSeconds(PlayerClass pc, String abilityId, String subKey, int fallbackSeconds) {
        return plugin.getConfig().getInt(path(pc, abilityId, "duration-" + subKey), fallbackSeconds);
    }
    private String path(PlayerClass pc, String abilityId, String key) {
        return "classes." + pc.name() + ".abilities." + abilityId + "." + key;
    }

    public ClassTheme themeOf(PlayerClass pc) { return themes.get(pc); }
    private void rebuildThemes() {
        for (PlayerClass pc : PlayerClass.values()) {
            String base = "classes." + pc.name();
            FileConfiguration config = plugin.getConfig();
            themes.put(pc, new ClassTheme(
                    parseColor(config.getString(base + ".theme.primary"), DEFAULTS.primary(pc)),
                    parseColor(config.getString(base + ".theme.secondary"), DEFAULTS.secondary(pc)),
                    config.getString(base + ".theme.symbol", DEFAULTS.symbol(pc)),
                    parseParticle(config.getString(base + ".cast-particle"), DEFAULTS.particle(pc)),
                    parseSound(config.getString(base + ".cast-sound"), Sound.ENTITY_PLAYER_LEVELUP)));
        }
    }
    private TextColor parseColor(String hex, String fallbackHex) {
        TextColor parsed = hex == null ? null : TextColor.fromHexString(hex);
        if (parsed != null) return parsed;
        TextColor fallback = TextColor.fromHexString(fallbackHex);
        return fallback != null ? fallback : TextColor.color(0x45E08A);
    }
    private Particle parseParticle(String name, Particle fallback) {
        if (name == null) return fallback;
        try { return Particle.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return fallback; }
    }
    private Sound parseSound(String name, Sound fallback) {
        if (name == null) return fallback;
        Sound parsed = Registry.SOUNDS.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        return parsed != null ? parsed : fallback;
    }

    public record AbilityDefaults(int unlock, int cost, int cooldown, String name,
                                  int duration, String description) {
    }

    private static final class DEFAULTS {
        static double resourceRegen(PlayerClass pc) {
            return switch (pc) {
                case WARRIOR -> -5.0;
                case HUNTER -> 5.0;
                case PRIEST, MAGE -> 2.0; // legacy: маг больше не использует эту ветку (пакет 5b)
                case ROGUE -> 10.0;
            };
        }
        static double resourceOnDeal(PlayerClass pc) { return pc == PlayerClass.WARRIOR ? 10.0 : 0.0; }
        static double resourceOnTake(PlayerClass pc) { return pc == PlayerClass.WARRIOR ? 10.0 : 0.0; }
        static double resourceOnHeal(PlayerClass pc) { return pc == PlayerClass.PRIEST ? 5.0 : 0.0; }
        static String primary(PlayerClass pc) {
            return switch (pc) {
                case WARRIOR -> "#8B0000";
                case HUNTER -> "#1B4D1B";
                case PRIEST -> "#B8B8B8";
                case MAGE -> "#101A6E";
                case ROGUE -> "#2A0A3A";
            };
        }
        static String secondary(PlayerClass pc) {
            return switch (pc) {
                case WARRIOR -> "#FF4500";
                case HUNTER -> "#3FA33F";
                case PRIEST -> "#FFF6D8";
                case MAGE -> "#3F6FFF";
                case ROGUE -> "#8B2FBF";
            };
        }
        static String symbol(PlayerClass pc) {
            return switch (pc) {
                case WARRIOR -> "⚔";
                case HUNTER -> "➳";
                case PRIEST -> "✚";
                case MAGE -> "✦";
                case ROGUE -> "☠";
            };
        }
        static Particle particle(PlayerClass pc) {
            return switch (pc) {
                case WARRIOR -> Particle.FLAME;
                case HUNTER -> Particle.CRIT;
                case PRIEST -> Particle.ENCHANTED_HIT;
                case MAGE -> Particle.PORTAL;
                case ROGUE -> Particle.SOUL_FIRE_FLAME;
            };
        }
        static String passiveDisplayName(PlayerClass pc, String id, String fallback) {
            String name = switch (pc) {
                case WARRIOR -> "execute_passive".equals(id) ? "Казнь" : null;
                case HUNTER -> "predator".equals(id) ? "Хищник" : null;
                case PRIEST -> "grace".equals(id) ? "Благодать" : null;
                case MAGE -> "mana_soaked".equals(id) ? "Пропитанный маной" : null;
                case ROGUE -> switch (id) {
                    case "poisoned_blades" -> "Отравленные клинки";
                    case "sadism" -> "Садизм";
                    default -> null;
                };
            };
            return name != null ? name : fallback;
        }
        static String passiveDescription(PlayerClass pc, String id, String fallback) {
            String desc = switch (pc) {
                case WARRIOR -> "execute_passive".equals(id)
                        ? "20% шанс — ×3 урона по цели с ≤20% HP" : null;
                case HUNTER -> "predator".equals(id) ? "×1.2 урона, пока HP ≥ 80%" : null;
                case PRIEST -> "grace".equals(id) ? "×1.15 к исходящему лечению" : null;
                case MAGE -> "mana_soaked".equals(id) ? "мана ≥ 50 → −15% входящего урона" : null;
                case ROGUE -> switch (id) {
                    case "poisoned_blades" -> "30% шанс — Яд I на 2 с";
                    case "sadism" -> "+3 урона при атаке со спины";
                    default -> null;
                };
            };
            return desc != null ? desc : fallback;
        }
        static Map<String, AbilityDefaults> abilities(PlayerClass pc) {
            return switch (pc) {
                case WARRIOR -> Map.of(
                        "steel_skin", new AbilityDefaults(10, 30, 45, "Стальная кожа", 5, "−80% входящего урона на 5 с"),
                        "shield_bash", new AbilityDefaults(25, 20, 25, "Удар щитом", 3, "Slowness II + Blindness в радиусе 4, таунт мобов, 3 с"),
                        "blood_fury", new AbilityDefaults(50, 40, 30, "Кровавое безумие", 4, "4 с: 20% входящего урона возвращается агрессору"),
                        "war_god", new AbilityDefaults(75, 100, 300, "Бог войны", 8, "Сила II + Сопротивление I на 8 с"));
                case HUNTER -> Map.of(
                        "aimed_shot", new AbilityDefaults(10, 20, 15, "Прицельный выстрел", 0, "Следующая стрела ×2 урона + Slowness цели"),
                        "cheetah_aspect", new AbilityDefaults(25, 0, 60, "Аспект гепарда", 0, "Скорость II 8 с + иммунитет к урону падения 10 с"),
                        "multi_shot", new AbilityDefaults(50, 40, 25, "Мультивыстрел", 0, "Три стрелы веером"),
                        "barrage", new AbilityDefaults(75, 80, 120, "Заградительный огонь", 0, "Серия стрел по площади"));
                case PRIEST -> Map.of(
                        "lesser_heal", new AbilityDefaults(1, 10, 3, "Малое исцеление", 0, "Лечит 4 HP"),
                        "flash_heal", new AbilityDefaults(10, 20, 6, "Быстрое исцеление", 0, "Лечит 8 HP"),
                        "pw_shield", new AbilityDefaults(25, 30, 30, "Слово силы: Щит", 6, "Поглощает 8 урона, 6 с"),
                        "circle_of_prayer", new AbilityDefaults(50, 50, 60, "Круг молитвы", 0, "Лечит союзников в радиусе 6"),
                        "smite", new AbilityDefaults(75, 60, 90, "Кара", 0, "Молния по цели"));
                case MAGE -> Map.of(
                        "firebolt", new AbilityDefaults(1, 10, 2, "Огненная стрела", 0, "Огненный снаряд, поджигает цель"),
                        "blink", new AbilityDefaults(25, 20, 20, "Скачок", 0, "Телепорт вперёд на 8 блоков"),
                        "frost_nova", new AbilityDefaults(50, 40, 45, "Кольцо льда", 4, "Замораживает врагов в радиусе 5 на 4 с"),
                        "arcane_burst", new AbilityDefaults(75, 100, 180, "Чародейский взрыв", 0, "Взрыв тайной энергии по площади"));
                case ROGUE -> Map.of(
                        "stealth", new AbilityDefaults(10, 30, 30, "Скрытность", 15, "Невидимость 15 с или до первого удара"),
                        "fan_of_knives", new AbilityDefaults(25, 25, 15, "Веер ножей", 0, "4 урона по радиусу 3"),
                        "cheap_shot", new AbilityDefaults(50, 40, 40, "Подлый удар", 2, "Blind + Slowness 2 с + 3 урона"),
                        "evasion", new AbilityDefaults(75, 60, 120, "Уклонение", 4, "100% уклонение от урона, 4 с"));
            };
        }
    }
}

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
 * Конфиг-слой плагина.
 * 1.7.5.1: самолечение битых значений damage-types.vanilla-map при reload.
 * 1.7.6: ключ balance.target-ttk-seconds — якорь TTK-харнесса (подсветка матрицы).
 * 1.9.3: addDefault для attributes.hp.per-level и main-str-bonus (ЖИВЫЕ ключи).
 */
public final class RaskolConfig {

    /** Канонические значения vanilla-map для самолечения. */
    private static final Map<String, String> VANILLA_MAP_DEFAULTS = Map.ofEntries(
            Map.entry("SONIC_BOOM", "true"),
            Map.entry("VOID", "true"),
            Map.entry("FALL", "true"),
            Map.entry("DROWNING", "true"),
            Map.entry("SUFFOCATION", "true"),
            Map.entry("STARVATION", "true"),
            Map.entry("FIRE", "magic"),
            Map.entry("FIRE_TICK", "magic"),
            Map.entry("LAVA", "magic"),
            Map.entry("HOT_FLOOR", "magic"),
            Map.entry("POISON", "magic"),
            Map.entry("WITHER", "magic"),
            Map.entry("MAGIC", "magic"),
            Map.entry("DRAGON_BREATH", "magic"),
            Map.entry("FREEZING", "magic"),
            Map.entry("LIGHTNING", "magic"));

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

        config.addDefault("hotbar-bind.enabled", true);
        config.addDefault("hotbar-bind.material", "AMETHYST_SHARD");
        config.addDefault("hotbar-bind.cooldown-bar", true);

        config.addDefault("target-cast.enabled", true);
        config.addDefault("target-cast.debug", false);

        config.addDefault("compat.authme-gate", true);
        config.addDefault("compat.block-casts-in-creative", true);

        config.addDefault("hooks.raskolcore.enabled", true);

        config.addDefault("hud.boss-bar.enabled", true);
        config.addDefault("hud.boss-bar.min-duration-seconds", 8);
        config.addDefault("hud.boss-bar.max-visible", 2);
        config.addDefault("hud.boss-bar.tick-period", 5);
        config.addDefault("hud.boss-bar.format", "{name} — {sec}с");
        config.addDefault("hud.boss-bar.format-infinite", "{name}");

        config.addDefault("performance.purge-interval-ticks", 1200);
        config.addDefault("performance.cast-click-cooldown-ms", 150L);
        config.addDefault("performance.sound-budget-per-tick", 8);

        config.addDefault("performance.ready-notify.enabled", true);
        config.addDefault("performance.ready-notify.min-cooldown-seconds", 30);
        config.addDefault("performance.ready-notify.sound", "ENTITY_PLAYER_LEVELUP");
        config.addDefault("performance.ready-notify.message", "{ability} — готова");
        config.addDefault("performance.ready-notify.spec-enabled", true);

        config.addDefault("class-accept.enabled", true);
        config.addDefault("class-accept.subtitle", "Твой путь избран");

        // 1.7.6: якорь TTK-харнесса
        config.addDefault("balance.target-ttk-seconds", 20.0);

        // 1.6.0: резисты
        config.addDefault("resist.cap", 90.0);
        config.addDefault("resist.classes.WARRIOR.magic", 12.0);
        config.addDefault("resist.classes.WARRIOR.physical", 27.0);
        config.addDefault("resist.classes.ROGUE.magic", 12.0);
        config.addDefault("resist.classes.ROGUE.physical", 14.0);
        config.addDefault("resist.classes.MAGE.magic", 26.0);
        config.addDefault("resist.classes.MAGE.physical", 12.0);
        config.addDefault("resist.classes.PRIEST.magic", 30.0);
        config.addDefault("resist.classes.PRIEST.physical", 16.0);
        config.addDefault("resist.classes.HUNTER.magic", 12.0);
        config.addDefault("resist.classes.HUNTER.physical", 16.0);

        // 1.7.5.1: летальная среда и анти-ваншот — с addDefault
        config.addDefault("damage-types.env-lethal-scale", true);
        config.addDefault("damage-types.env-lethal",
                List.of("FALL", "DROWNING", "SUFFOCATION", "STARVATION"));
        config.addDefault("combat.max-single-hit-pct", 35.0);
        config.addDefault("combat.cap-exempt-causes",
                List.of("FALL", "DROWNING", "SUFFOCATION", "STARVATION", "VOID", "SONIC_BOOM"));
        for (Map.Entry<String, String> e : VANILLA_MAP_DEFAULTS.entrySet()) {
            config.addDefault("damage-types.vanilla-map." + e.getKey(), e.getValue());
        }

        config.addDefault("installations.bear_trap.damage-physical", 3.0);
        config.addDefault("installations.frost_rune.damage-magic", 4.0);

        // 1.7.0.5 + 1.9.3: HP-формула — живые ключи с addDefault
        config.addDefault("attributes.hp.base-hp", 100.0);
        config.addDefault("attributes.hp.per-str", 20.0);
        config.addDefault("attributes.hp.per-level", 5.0);          // 1.9.3: HP за уровень
        config.addDefault("attributes.hp.main-str-bonus", 8.0);     // 1.9.3: бонус STR-main
        config.addDefault("attributes.hp.regen-per-str", 0.025);
        config.addDefault("attributes.hp.regen-combat-factor", 0.35);
        config.addDefault("attributes.hp.regen-cap-pct", 1.5);

        config.addDefault("messages.no-class", "Класс не выбран — посетите герольда");
        config.addDefault("messages.no-class-cast", "Класс не выбран — способности недоступны");
        config.addDefault("messages.no-permission", "Недостаточно прав");
        config.addDefault("messages.unlock", "«{ability}» откроется на уровне {required} (у вас {current})");
        config.addDefault("messages.cooldown", "«{ability}»: перезарядка ещё {seconds}с");
        config.addDefault("messages.no-resource", "Не хватает ресурса «{resource}»: нужно {cost}, у вас {value}");
        config.addDefault("messages.activated", "«{ability}» — активирована");
        config.addDefault("messages.blink-unsafe", "Скачок невозможен: нет безопасной точки");
        config.addDefault("messages.cheap-shot-no-target", "Нет цели в радиусе действия");
        config.addDefault("messages.tag.execute", "Казнь ×3!");
        config.addDefault("messages.tag.predator", "Хищник!");
        config.addDefault("messages.tag.poison", "Яд!");
        config.addDefault("messages.tag.backstab", "В спину +3!");
        config.addDefault("messages.healed-target", "✚ {target}: +{amount} HP");
        config.addDefault("messages.healed-you", "{caster} исцелил тебя");
        config.addDefault("messages.shield-target", "Щит на: {target}");
        config.addDefault("messages.shield-you", "{caster} наложил на тебя щит");
        config.addDefault("messages.target-full-hp", "Цель здорова");
        config.addDefault("messages.gate.blocked", "Способности недоступны в этом режиме или до входа в аккаунт.");
        config.addDefault("messages.gate.blocked.install", "Инсталляции недоступны в этом режиме или до входа в аккаунт.");
        config.addDefault("messages.book.title", "Книга класса: ");
        config.addDefault("messages.book.tab.abilities", "Способности");
        config.addDefault("messages.book.tab.specs", "Специализации");
        config.addDefault("messages.book.tab.class", "Класс и пассивки");
        config.addDefault("messages.book.tab.hint", "Клик — открыть вкладку");
        config.addDefault("messages.book.cost", "Цена: {cost} {resource}");
        config.addDefault("messages.book.cooldown", "Кулдаун: {sec} с");
        config.addDefault("messages.book.recharging", "Перезарядка: {sec} с");
        config.addDefault("messages.book.unlock", "Открытие: уровень {level}");
        config.addDefault("messages.book.scroll.have", "Свиток: в инвентаре ({count})");
        config.addDefault("messages.book.scroll.none", "Свиток: нет");
        config.addDefault("messages.book.use.left", "ЛКМ — применить");
        config.addDefault("messages.book.use.right", "ПКМ — свиток в хотбар");
        config.addDefault("messages.book.place.left", "ЛКМ — поставить здесь");
        config.addDefault("messages.book.place.right", "ПКМ — свиток постановки");
        config.addDefault("messages.book.install.active", "Активно: {count}/2 · TTL {ttl} с");
        config.addDefault("messages.book.spec.passive", "Пассив: {text}");
        config.addDefault("messages.book.spec.chosen", "Выбрана тобой");
        config.addDefault("messages.book.spec.notchosen", "Не выбрана · ПКМ — выбрать (уровень 40+)");
        config.addDefault("messages.book.spec.other", "Выбрана другая спека — отречение ниже");
        config.addDefault("messages.book.respec.title", "Отречение от пути");
        config.addDefault("messages.book.respec.nospec", "Спеки нет — отрекаться не от чего");
        config.addDefault("messages.book.respec.current", "Текущая спека: {name}");
        config.addDefault("messages.book.respec.price", "Цена: {price} монет (сжигаются)");
        config.addDefault("messages.book.respec.hint", "ПКМ №1 — взвести, ПКМ №2 (30 с) — отречься");
        config.addDefault("messages.book.msg.scroll.got", "Свиток получен: ");
        config.addDefault("messages.book.msg.scroll.dup", "Свиток уже в инвентаре — дубль не выдан.");
        config.addDefault("messages.book.msg.spec.other", "Спека уже выбрана: {name}. Отречение — кристалл ниже.");
        config.addDefault("messages.book.msg.respec.none", "Спеки нет — отрекаться не от чего.");
        config.addDefault("messages.book.msg.respec.arm", "Отречение взведено: ПКМ по кристаллу ещё раз в течение 30 с. Цена: {price} монет");
        config.addDefault("messages.book.msg.respec.ok", "Путь сброшен. Выбери новую спеку.");
        config.addDefault("messages.book.msg.respec.poor", "Не хватает монет на отречение.");
        config.addDefault("messages.book.msg.respec.noecon", "Экономика недоступна — респец отключён.");
        config.addDefault("messages.book.resource.warrior", "Ярость: −5/с вне боя; +10 за урон (нанёс/получил)");
        config.addDefault("messages.book.resource.hunter", "Концентрация: +5/с вне боя, 0 в бою");
        config.addDefault("messages.book.resource.priest", "Свет: +2/с всегда; +5 за событие лечения");
        config.addDefault("messages.book.resource.mage", "Мана: +3/4/5/6 в секунду по порогам 25/50/75");
        config.addDefault("messages.book.resource.rogue", "Энергия: +10/с");
        config.addDefault("messages.book.emblem.resource", "Ресурс сейчас: {value}/100");
        config.addDefault("messages.book.emblem.crown", "Корона: {name}");
        config.addDefault("messages.book.crown.title", "Корона и титул");
        config.addDefault("messages.book.crown.crown", "Корона: {name}");
        config.addDefault("messages.book.crown.titleline", "Титул: {name}");
        config.addDefault("messages.book.crown.aura", "Аура-партикл видна союзникам и врагам");

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

        config.addDefault("classes.MAGE.regen-tier-1", 3.0);
        config.addDefault("classes.MAGE.regen-tier-2", 4.0);
        config.addDefault("classes.MAGE.regen-tier-3", 5.0);
        config.addDefault("classes.MAGE.regen-tier-4", 6.0);

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

        config.options().copyDefaults(true);
        healVanillaMap(config);
        plugin.saveConfig();
        rebuildThemes();
    }

    /** 1.7.5.1: пустые/битые значения vanilla-map восстанавливаются до канона. */
    private void healVanillaMap(FileConfiguration config) {
        boolean dirty = false;
        for (Map.Entry<String, String> e : VANILLA_MAP_DEFAULTS.entrySet()) {
            String path = "damage-types.vanilla-map." + e.getKey();
            String current = config.getString(path, null);
            if (current == null || current.isEmpty()) {
                config.set(path, e.getValue());
                dirty = true;
            }
        }
        if (dirty) {
            plugin.getLogger().info("config: восстановлены пустые значения damage-types.vanilla-map (1.7.5.1)");
        }
    }

    /** 1.7.6: якорь TTK для подсветки матрицы симулятора. */
    public double targetTtkSeconds() {
        double v = plugin.getConfig().getDouble("balance.target-ttk-seconds", 20.0);
        return Double.isFinite(v) && v > 0.0 ? v : 20.0;
    }

    public boolean isHudEnabled() { return plugin.getConfig().getBoolean("hud.enabled", true); }
    public int hudUpdatePeriodTicks() { return plugin.getConfig().getInt("hud.update-period-ticks", 10); }
    public boolean hudSkipSpectator() { return plugin.getConfig().getBoolean("hud.skip-spectator", true); }
    public boolean hudFullResourceAura() { return plugin.getConfig().getBoolean("hud.full-resource-aura", true); }
    public boolean hudDebugSkip() { return plugin.getConfig().getBoolean("hud.debug-skip", false); }

    public boolean isBindEnabled() { return plugin.getConfig().getBoolean("hotbar-bind.enabled", true); }
    public String bindMaterial() { return plugin.getConfig().getString("hotbar-bind.material", "AMETHYST_SHARD"); }

    public boolean isTargetCastEnabled() { return plugin.getConfig().getBoolean("target-cast.enabled", true); }
    public boolean isTargetCastDebug() { return plugin.getConfig().getBoolean("target-cast.debug", false); }

    public boolean authMeGate() { return plugin.getConfig().getBoolean("compat.authme-gate", true); }
    public boolean blockCastsInCreative() { return plugin.getConfig().getBoolean("compat.block-casts-in-creative", true); }

    public boolean raskolCoreEnabled() { return plugin.getConfig().getBoolean("hooks.raskolcore.enabled", true); }

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
                case PRIEST, MAGE -> 2.0;
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
                        "tyr_strike", new AbilityDefaults(10, 20, 8, "Удар Тира", 0,
                                "Тяжёлый удар бога войны: физ-урон, скалируется от Силы оружия"),
                        "balder_skin", new AbilityDefaults(25, 25, 30, "Шкура Бальдра", 5,
                                "Кожа неуязвимого бога: +физрезист на 5 с (скалируется от Силы оружия)"),
                        "berserkergang", new AbilityDefaults(50, 35, 45, "Берсеркерганг", 6,
                                "Ярость берсерка: Сила II + Сопротивление I на 6 с"),
                        "fenrir_blood", new AbilityDefaults(65, 30, 25, "Кровь Фенрира", 0,
                                "Волчья кровь лечит тебя (хил скалируется от Силы оружия)"),
                        "ragnarok", new AbilityDefaults(75, 60, 60, "Рагнарёк", 0,
                                "Сумерки богов: тяжёлый удар; цель ниже 25% HP получает ×3"));
                case HUNTER -> Map.of(
                        "wolf_mark", new AbilityDefaults(10, 20, 12, "Метка Волка", 0,
                                "Ведьмачья метка: урон, Slowness I 3 с и подсветка цели на 6 с"),
                        "swallow", new AbilityDefaults(25, 15, 40, "Ласточка", 8,
                                "Ведьмачье зелье: Скорость II + Регенерация I на 8 с"),
                        "piercing_shot", new AbilityDefaults(50, 30, 20, "Пронзающий выстрел", 0,
                                "Бронебойный выстрел: тяжёлый одиночный урон (скалируется от Силы оружия)"),
                        "arrow_fan", new AbilityDefaults(65, 35, 22, "Веер стрел", 0,
                                "Три стрелы конусом, урон каждой скалируется от Силы оружия"),
                        "arrow_rain", new AbilityDefaults(75, 60, 90, "Дождь стрел", 0,
                                "Шквал стрел по площади радиусом 5 (не проходит сквозь стены)"));
                case PRIEST -> Map.of(
                        "saint_tear", new AbilityDefaults(10, 10, 3, "Слеза Святой", 0,
                                "Литургический хил: восстанавливает HP себе или союзнику (скалируется от Силы исцеления)"),
                        "word_of_life", new AbilityDefaults(25, 20, 6, "Слово Жизни", 0,
                                "Мощное слово: восстанавливает много HP себе или союзнику (скалируется от Силы исцеления)"),
                        "aegis_faith", new AbilityDefaults(50, 30, 30, "Эгида Веры", 5,
                                "Щит веры: +физрезист и +магрезист на 5 с (скалируется от Силы исцеления)"),
                        "circle_elysium", new AbilityDefaults(65, 50, 60, "Круг Элизия", 0,
                                "Лечит себя и всех союзников в радиусе 6 (скалируется от Силы исцеления)"),
                        "wrath_heaven", new AbilityDefaults(75, 60, 90, "Кара Небес", 0,
                                "Карая кара: тяжёлый маг-урон; цель ниже 25% HP получает ×3"));
                case MAGE -> Map.of(
                        "fire_prometheus", new AbilityDefaults(10, 15, 6, "Огонь Прометея", 0,
                                "Дар титана: гибридный урон 30/70 (физ/маг) + поджог 3 с"),
                        "hermes_step", new AbilityDefaults(25, 20, 20, "Шаг Гермеса", 0,
                                "Мгновенный рывок вперёд на 8 блоков (проверяет безопасность точки)"),
                        "boreas_breath", new AbilityDefaults(50, 40, 45, "Дыхание Борея", 4,
                                "Ледяной шквал: маг-урон по площади радиусом 5 + Slowness II 4 с (не сквозь стены)"),
                        "athena_aegis", new AbilityDefaults(65, 30, 30, "Эгида Афины", 5,
                                "Щит богини: +магрезист на 5 с (скалируется от Силы заклинаний)"),
                        "zeus_wrath", new AbilityDefaults(75, 60, 90, "Гнев Зевса", 0,
                                "Карая молния: тяжёлый маг-урон; цель ниже 25% HP получает ×3"));
                case ROGUE -> Map.of(
                        "shadow_cloak", new AbilityDefaults(10, 30, 30, "Плащ теней", 15,
                                "Слиться с тенью: Невидимость 15 с"),
                        "blade_fan", new AbilityDefaults(25, 25, 15, "Веер клинков", 0,
                                "Вихрь ножей по площади радиусом 3 (не сквозь стены)"),
                        "strangle", new AbilityDefaults(50, 40, 40, "Удушение палача", 0,
                                "Хватка палача: урон + Blind 2 с + Slowness 2 с"),
                        "borgia_poison", new AbilityDefaults(65, 35, 30, "Яд Борджа", 0,
                                "Отравленный клинок: урон + Яд I 5 с"),
                        "shadow_dance", new AbilityDefaults(75, 60, 120, "Танец теней", 4,
                                "Танец клинков: +30 ЛОВКОСТИ на 4 с (всплеск уклонения через avoidance)"));
            };
        }
    }
}

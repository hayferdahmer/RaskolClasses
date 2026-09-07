// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.fx;

import dev.raskol.classes.RaskolClasses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Движок звука/партиклов (1.5.0 + 1.5.3 + 1.5.4).
 * 1.5.4: каталог пересмотрен под темы классов — звуки различимы и соответствуют
 * описанию способности:
 *  - Жрец (свет/магия): колокол и аметистовый перезвон вместо «железа воина»;
 *  - Воин (металл/война): наковальня, лёд доспеха, гром аватара на «Боге войны»;
 *  - Охотник (лук/зверь): арбалетный выстрел на прицеле, кошачье шипение на аспекте;
 *  - Маг (аркана/стихии) и Разбойник (тень/дым) — без изменений, темы уже читались.
 * Резолв имён — индекс реестра по нормализованному ключу + алиасы (1.5.3.1).
 * Валидация vfx.* на старте и /rc reload (1.5.3).
 */
public final class FxService {

    /** Дефолтный каталог: id -> [звук каста, партикл каста]. */
    private static final Map<String, String[]> DEFAULTS = new HashMap<>();

    static {
        // Воин: металл и война
        DEFAULTS.put("steel_skin", new String[]{"ITEM_ARMOR_EQUIP_IRON", "CRIT"});
        DEFAULTS.put("shield_bash", new String[]{"BLOCK_ANVIL_LAND", "SWEEP_ATTACK"});
        DEFAULTS.put("blood_fury", new String[]{"ENTITY_RAVAGER_ROAR", "CRIMSON_SPORE"});
        DEFAULTS.put("war_god", new String[]{"ENTITY_LIGHTNING_BOLT_THUNDER", "FLAME"});
        // Охотник: лук и зверь
        DEFAULTS.put("aimed_shot", new String[]{"ITEM_CROSSBOW_SHOOT", "CRIT"});
        DEFAULTS.put("cheetah_aspect", new String[]{"ENTITY_CAT_HISS", "WHITE_ASH"});
        DEFAULTS.put("multi_shot", new String[]{"ENTITY_ARROW_SHOOT", "SWEEP_ATTACK"});
        DEFAULTS.put("barrage", new String[]{"ENTITY_ARROW_SHOOT", "POOF"});
        // Жрец: свет и магия (1.5.4: колокол/перезвон вместо щита воина)
        DEFAULTS.put("lesser_heal", new String[]{"BLOCK_BELL_USE", "HEART"});
        DEFAULTS.put("flash_heal", new String[]{"BLOCK_AMETHYST_BLOCK_CHIME", "HEART"});
        DEFAULTS.put("pw_shield", new String[]{"BLOCK_AMETHYST_BLOCK_CHIME", "ENCHANTED_HIT"});
        DEFAULTS.put("circle_of_prayer", new String[]{"BLOCK_BEACON_ACTIVATE", "HEART"});
        DEFAULTS.put("smite", new String[]{"ENTITY_LIGHTNING_BOLT_IMPACT", "FLASH"});
        // Маг: аркана и стихии
        DEFAULTS.put("firebolt", new String[]{"ITEM_FIRECHARGE_USE", "FLAME"});
        DEFAULTS.put("blink", new String[]{"ENTITY_ENDERMAN_TELEPORT", "PORTAL"});
        DEFAULTS.put("frost_nova", new String[]{"ENTITY_PLAYER_HURT_FREEZE", "SNOWFLAKE"});
        DEFAULTS.put("arcane_burst", new String[]{"ENTITY_EVOKER_CAST_SPELL", "POOF"});
        // Разбойник: тень и дым
        DEFAULTS.put("stealth", new String[]{"ENTITY_PHANTOM_FLAP", "SMOKE"});
        DEFAULTS.put("fan_of_knives", new String[]{"ENTITY_PLAYER_ATTACK_SWEEP", "SWEEP_ATTACK"});
        DEFAULTS.put("cheap_shot", new String[]{"ENTITY_PLAYER_ATTACK_KNOCKBACK", "SMOKE"});
        DEFAULTS.put("evasion", new String[]{"ENTITY_ENDERMAN_TELEPORT", "CLOUD"});
        // Активки специализаций
        DEFAULTS.put("challenge", new String[]{"BLOCK_BELL_USE", "ANGRY_VILLAGER"});
        DEFAULTS.put("rage_burst", new String[]{"ENTITY_PLAYER_ATTACK_CRIT", "CRIMSON_SPORE"});
        DEFAULTS.put("precise_shot", new String[]{"BLOCK_NOTE_BLOCK_PLING", "END_ROD"});
        DEFAULTS.put("snare", new String[]{"BLOCK_TRIPWIRE_ATTACH", "CRIT"});
        DEFAULTS.put("sanctuary", new String[]{"BLOCK_BEACON_ACTIVATE", "HEART"});
        DEFAULTS.put("mind_spike", new String[]{"ENTITY_ENDERMAN_STARE", "REVERSE_PORTAL"});
        DEFAULTS.put("arcane_flow", new String[]{"BLOCK_ENCHANTING_TABLE_USE", "ENCHANTED_HIT"});
        DEFAULTS.put("ice_ring", new String[]{"ENTITY_PLAYER_HURT_FREEZE", "SNOWFLAKE"});
        DEFAULTS.put("garrote", new String[]{"ENTITY_PLAYER_ATTACK_WEAK", "DAMAGE_INDICATOR"});
        DEFAULTS.put("smoke_bomb", new String[]{"BLOCK_FIRE_EXTINGUISH", "SMOKE"});
        // Проки пассивок
        DEFAULTS.put("proc.execute_passive", new String[]{"ENTITY_PLAYER_ATTACK_CRIT", "DAMAGE_INDICATOR"});
        DEFAULTS.put("proc.predator", new String[]{"ENTITY_PLAYER_ATTACK_STRONG", "CRIT"});
        DEFAULTS.put("proc.grace", new String[]{"ENTITY_EXPERIENCE_ORB_PICKUP", "HEART"});
        DEFAULTS.put("proc.mana_soaked", new String[]{"ENTITY_PLAYER_BREATH", "ENCHANTED_HIT"});
        DEFAULTS.put("proc.poisoned_blades", new String[]{"ENTITY_SPIDER_STEP", "COMPOSTER"});
        DEFAULTS.put("proc.sadism", new String[]{"ENTITY_PLAYER_ATTACK_SWEEP", "DAMAGE_INDICATOR"});
        DEFAULTS.put("proc.crit_liquidator", new String[]{"ENTITY_PLAYER_ATTACK_CRIT", "CRIT"});
        DEFAULTS.put("proc.dodge_trickster", new String[]{"ENTITY_ENDERMAN_TELEPORT", "CLOUD"});
        DEFAULTS.put("proc.lifesteal_shadowweaver", new String[]{"ENTITY_PLAYER_LEVELUP", "HEART"});
    }

    /** Алиасы для имён, переименованных или отсутствующих на части билдов. */
    private static final Map<String, String> SOUND_ALIASES = new HashMap<>();

    static {
        SOUND_ALIASES.put("BLOCK_ENCHANTMENT_TABLE_USE", "BLOCK_ENCHANTING_TABLE_USE");
        SOUND_ALIASES.put("ITEM_ARMOR_EQUIP_IRON", "BLOCK_ANVIL_LAND");
    }

    private static volatile Map<String, Sound> soundIndexCache;
    private static volatile Map<String, Particle> particleIndexCache;

    private final RaskolClasses plugin;

    public FxService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /** Безусловный VFX каста (спеки, слот 6). */
    public void onCast(Player player, String abilityId) {
        castVfx(player, abilityId);
    }

    /** VFX каста классовых абилок (1–5) только при «свежем» кулдауне. */
    public void onAttempt(Player player, String abilityId, long cooldownMillis) {
        if (cooldownMillis > 0) {
            long remaining = plugin.getCooldowns()
                    .getRemainingMillis(player.getUniqueId(), abilityId);
            if (remaining <= 0 || remaining < cooldownMillis - 300L) {
                return;
            }
        }
        castVfx(player, abilityId);
    }

    private void castVfx(Player player, String id) {
        FileConfiguration cfg = plugin.getConfig();
        String[] def = DEFAULTS.getOrDefault(id, new String[]{"", ""});
        String soundKey = cfg.getString("vfx." + id + ".cast-sound", def[0]);
        String particleKey = cfg.getString("vfx." + id + ".cast-particle", def[1]);
        apply(player.getLocation(), player, soundKey, particleKey, false);
    }

    /** Фидбек прока пассивки/спека: партикл + звук + (опц.) actionbar-тег. */
    public void procByKey(Player player, String fallbackTag, String procId) {
        FileConfiguration cfg = plugin.getConfig();
        String[] def = DEFAULTS.getOrDefault("proc." + procId, new String[]{"", ""});
        String soundKey = cfg.getString("vfx.proc." + procId + ".cast-sound", def[0]);
        String particleKey = cfg.getString("vfx.proc." + procId + ".cast-particle", def[1]);
        String tag = cfg.getString("vfx.proc." + procId + ".tag", fallbackTag);
        apply(player.getLocation(), player, soundKey, particleKey, true);
        if (tag != null && !tag.isEmpty()
                && cfg.getBoolean("vfx.proc-actionbar", false)) {
            player.sendActionBar(Component.text(tag, NamedTextColor.YELLOW));
        }
    }

    /** Legacy-обёртка обратной совместимости. */
    public void proc(Player player, String tag, Particle particle, Sound sound) {
        if (particle != null) {
            player.spawnParticle(particle,
                    player.getLocation().add(0.0, 1.0, 0.0),
                    6, 0.3, 0.3, 0.3, 0.0);
        }
        if (sound != null) {
            playSound(player.getLocation(), sound, 0.4f, 1.2f);
        }
    }

    private void apply(Location origin, Player player,
                       String soundKey, String particleKey, boolean proc) {
        Sound sound = resolveSound(soundKey);
        if (sound != null) {
            playSound(origin, sound, proc ? 0.4f : 0.6f, proc ? 1.2f : 1.0f);
        }
        Particle particle = resolveParticle(particleKey);
        if (particle != null) {
            player.spawnParticle(particle,
                    player.getLocation().add(0.0, 1.0, 0.0),
                    proc ? 6 : 12, 0.3, proc ? 0.3 : 0.4, 0.3, 0.0);
        }
    }

    /** Звук в точке (затухает с дистанцией, ~16 блоков). */
    public void playSound(Location loc, Sound sound, float volume, float pitch) {
        if (loc.getWorld() != null) {
            loc.getWorld().playSound(loc, sound, volume, pitch);
        }
    }

    // --- 1.5.3.1: резолв через индекс реестра + алиасы ---

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[._-]", "");
    }

    private static Map<String, Sound> soundIndex() {
        Map<String, Sound> local = soundIndexCache;
        if (local == null) {
            synchronized (FxService.class) {
                local = soundIndexCache;
                if (local == null) {
                    local = new HashMap<>();
                    for (Sound s : Registry.SOUNDS) {
                        local.putIfAbsent(norm(s.name()), s);
                    }
                    soundIndexCache = local;
                }
            }
        }
        return local;
    }

    private static Map<String, Particle> particleIndex() {
        Map<String, Particle> local = particleIndexCache;
        if (local == null) {
            synchronized (FxService.class) {
                local = particleIndexCache;
                if (local == null) {
                    local = new HashMap<>();
                    for (Particle p : Registry.PARTICLE_TYPE) {
                        local.putIfAbsent(norm(p.name()), p);
                    }
                    particleIndexCache = local;
                }
            }
        }
        return local;
    }

    /** Безопасный резолв звука: индекс → алиасы → прямые ключи; иначе null. */
    public Sound resolveSound(String key) {
        String current = key;
        for (int hop = 0; hop < 3 && current != null && !current.isEmpty(); hop++) {
            String lower = current.toLowerCase(Locale.ROOT);
            Sound s = soundIndex().get(norm(lower));
            if (s != null) {
                return s;
            }
            s = Registry.SOUNDS.get(NamespacedKey.minecraft(lower.replace('_', '.')));
            if (s != null) {
                return s;
            }
            s = Registry.SOUNDS.get(NamespacedKey.minecraft(lower));
            if (s != null) {
                return s;
            }
            current = SOUND_ALIASES.get(current.toUpperCase(Locale.ROOT));
        }
        return null;
    }

    /** Безопасный резолв партикла: индекс → прямые ключи; иначе null. */
    public Particle resolveParticle(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        Particle p = particleIndex().get(norm(lower));
        if (p != null) {
            return p;
        }
        p = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(lower.replace('_', '.')));
        if (p != null) {
            return p;
        }
        return Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(lower));
    }

    // --- 1.5.3: диагностика каталога vfx ---

    public int validateConfig() {
        int problems = scan(true);
        if (problems == 0) {
            plugin.getLogger().info("FxService: каталог vfx валиден — все имена резолвятся.");
        } else {
            plugin.getLogger().warning("FxService: в каталоге vfx " + problems
                    + " неизвестных имён — эти эффекты будут тихими.");
        }
        return problems;
    }

    public int countProblems() {
        return scan(false);
    }

    private int scan(boolean log) {
        int problems = 0;
        ConfigurationSection vfx = plugin.getConfig().getConfigurationSection("vfx");
        if (vfx == null) {
            return 0;
        }
        for (String key : vfx.getKeys(false)) {
            if (key.equals("proc") || key.equals("trails")) {
                continue;
            }
            ConfigurationSection entry = vfx.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            problems += checkEntry("vfx." + key, entry, log);
        }
        ConfigurationSection proc = vfx.getConfigurationSection("proc");
        if (proc != null) {
            for (String key : proc.getKeys(false)) {
                ConfigurationSection entry = proc.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                problems += checkEntry("vfx.proc." + key, entry, log);
            }
        }
        return problems;
    }

    private int checkEntry(String path, ConfigurationSection entry, boolean log) {
        int problems = 0;
        String sound = entry.getString("cast-sound", "");
        if (sound != null && !sound.isEmpty() && resolveSound(sound) == null) {
            if (log) {
                plugin.getLogger().warning("FxService: неизвестный звук '" + sound
                        + "' в " + path + ".cast-sound");
            }
            problems++;
        }
        String particle = entry.getString("cast-particle", "");
        if (particle != null && !particle.isEmpty() && resolveParticle(particle) == null) {
            if (log) {
                plugin.getLogger().warning("FxService: неизвестный партикл '" + particle
                        + "' в " + path + ".cast-particle");
            }
            problems++;
        }
        return problems;
    }

    /** Эффективные звук/партикл абилки (конфиг-оверрайд или дефолт). */
    public String describeFor(String abilityId) {
        FileConfiguration cfg = plugin.getConfig();
        String[] def = DEFAULTS.getOrDefault(abilityId, new String[]{"", ""});
        String soundKey = cfg.getString("vfx." + abilityId + ".cast-sound", def[0]);
        String particleKey = cfg.getString("vfx." + abilityId + ".cast-particle", def[1]);
        Sound s = resolveSound(soundKey);
        Particle p = resolveParticle(particleKey);
        return (s != null ? s.name() : "тишина") + " / "
                + (p != null ? p.name() : "без партикла");
    }

    /** Строки fx-каталога для /rc debug. */
    public void appendDebug(CommandSender sender, List<String> abilityIds) {
        int problems = countProblems();
        sender.sendMessage(Component.text("Fx: проблем каталога vfx: " + problems
                        + (problems == 0 ? " (все имена валидны)" : " — эти эффекты тихие"),
                problems == 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
        for (String id : abilityIds) {
            sender.sendMessage(Component.text("  • fx " + id + ": " + describeFor(id),
                    NamedTextColor.GRAY));
        }
    }
}

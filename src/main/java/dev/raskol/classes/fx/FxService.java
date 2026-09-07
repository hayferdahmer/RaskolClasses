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
 * Движок звука/партиклов (1.5.0 + 1.5.3).
 *  - каталог дефолтов вшит в код; конфиг vfx.* только переопределяет;
 *  - резолв через Registry.SOUNDS / Registry.PARTICLE_TYPE (без deprecated API);
 *  - FIX 1.5.3: validateConfig() при старте и /rc reload — варн на каждое
 *    неизвестное имя в vfx.* (раньше опечатка = тихая тишина без следа);
 *  - FIX 1.5.3: appendDebug(...) — строки fx-каталога в /rc debug.
 */
public final class FxService {

    /** Дефолтный каталог: id -> [звук каста, партикл каста]. */
    private static final Map<String, String[]> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put("steel_skin", new String[]{"ITEM_ARMOR_EQUIP_IRON", "CRIT"});
        DEFAULTS.put("shield_bash", new String[]{"BLOCK_ANVIL_LAND", "SWEEP_ATTACK"});
        DEFAULTS.put("blood_fury", new String[]{"ENTITY_RAVAGER_ROAR", "CRIMSON_SPORE"});
        DEFAULTS.put("war_god", new String[]{"ENTITY_EVOKER_CAST_SPELL", "FLAME"});
        DEFAULTS.put("aimed_shot", new String[]{"ENTITY_ARROW_SHOOT", "CRIT"});
        DEFAULTS.put("cheetah_aspect", new String[]{"ENTITY_PHANTOM_FLAP", "WHITE_ASH"});
        DEFAULTS.put("multi_shot", new String[]{"ENTITY_ARROW_SHOOT", "SWEEP_ATTACK"});
        DEFAULTS.put("barrage", new String[]{"ENTITY_ARROW_SHOOT", "POOF"});
        DEFAULTS.put("lesser_heal", new String[]{"ENTITY_EXPERIENCE_ORB_PICKUP", "HEART"});
        DEFAULTS.put("flash_heal", new String[]{"ENTITY_EXPERIENCE_ORB_PICKUP", "HEART"});
        DEFAULTS.put("pw_shield", new String[]{"ITEM_SHIELD_BLOCK", "ENCHANTED_HIT"});
        DEFAULTS.put("circle_of_prayer", new String[]{"BLOCK_BEACON_ACTIVATE", "HEART"});
        DEFAULTS.put("smite", new String[]{"ENTITY_LIGHTNING_BOLT_IMPACT", "FLASH"});
        DEFAULTS.put("firebolt", new String[]{"ITEM_FIRECHARGE_USE", "FLAME"});
        DEFAULTS.put("blink", new String[]{"ENTITY_ENDERMAN_TELEPORT", "PORTAL"});
        DEFAULTS.put("frost_nova", new String[]{"ENTITY_PLAYER_HURT_FREEZE", "SNOWFLAKE"});
        DEFAULTS.put("arcane_burst", new String[]{"ENTITY_EVOKER_CAST_SPELL", "POOF"});
        DEFAULTS.put("stealth", new String[]{"ENTITY_PHANTOM_FLAP", "SMOKE"});
        DEFAULTS.put("fan_of_knives", new String[]{"ENTITY_PLAYER_ATTACK_SWEEP", "SWEEP_ATTACK"});
        DEFAULTS.put("cheap_shot", new String[]{"ENTITY_PLAYER_ATTACK_KNOCKBACK", "SMOKE"});
        DEFAULTS.put("evasion", new String[]{"ENTITY_ENDERMAN_TELEPORT", "CLOUD"});
        DEFAULTS.put("challenge", new String[]{"BLOCK_BELL_USE", "ANGRY_VILLAGER"});
        DEFAULTS.put("rage_burst", new String[]{"ENTITY_PLAYER_ATTACK_CRIT", "CRIMSON_SPORE"});
        DEFAULTS.put("precise_shot", new String[]{"BLOCK_NOTE_BLOCK_PLING", "END_ROD"});
        DEFAULTS.put("snare", new String[]{"BLOCK_TRIPWIRE_ATTACH", "CRIT"});
        DEFAULTS.put("sanctuary", new String[]{"BLOCK_BEACON_ACTIVATE", "HEART"});
        DEFAULTS.put("mind_spike", new String[]{"ENTITY_ENDERMAN_STARE", "REVERSE_PORTAL"});
        DEFAULTS.put("arcane_flow", new String[]{"BLOCK_ENCHANTMENT_TABLE_USE", "ENCHANTED_HIT"});
        DEFAULTS.put("ice_ring", new String[]{"ENTITY_PLAYER_HURT_FREEZE", "SNOWFLAKE"});
        DEFAULTS.put("garrote", new String[]{"ENTITY_PLAYER_ATTACK_WEAK", "DAMAGE_INDICATOR"});
        DEFAULTS.put("smoke_bomb", new String[]{"BLOCK_FIRE_EXTINGUISH", "SMOKE"});
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

    private final RaskolClasses plugin;

    public FxService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /** Безусловный VFX каста (спеки, слот 6). */
    public void onCast(Player player, String abilityId) {
        castVfx(player, abilityId);
    }

    /**
     * VFX каста классовых абилок (1–5): играет только если кулдаун
     * «свежий» (запущен в последние 300 мс) — значит каст прошёл успешно.
     */
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

    /** Безопасный резолв звука: опечатка = null (тишина). */
    public Sound resolveSound(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        Sound s = Registry.SOUNDS.get(NamespacedKey.minecraft(lower.replace('_', '.')));
        if (s != null) {
            return s;
        }
        return Registry.SOUNDS.get(NamespacedKey.minecraft(lower));
    }

    /** Безопасный резолв партикла: опечатка = null. */
    public Particle resolveParticle(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        Particle p = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(lower.replace('_', '.')));
        if (p != null) {
            return p;
        }
        return Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(lower));
    }

    // --- FIX 1.5.3: диагностика каталога vfx ---

    /**
     * Проверка всех имён vfx.* (cast-sound/cast-particle, включая proc.*).
     * Логирует WARNING на каждое неизвестное имя + итог. Вызывается на старте
     * и в /rc reload. Возвращает число проблем.
     */
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

    /** Тихий подсчёт проблем (для /rc debug). */
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
                continue; // например, proc-actionbar: boolean
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

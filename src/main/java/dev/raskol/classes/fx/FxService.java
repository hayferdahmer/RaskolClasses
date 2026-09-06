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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Движок звука/партиклов (1.5.0, Пакеты 1+3).
 *  - castVfx — VFX каста абилок (слоты 1–6);
 *  - procByKey — VFX проков пассивок (execute_passive, predator, grace,
 *    mana_soaked, poisoned_blades, sadism, crit_liquidator,
 *    dodge_trickster, lifesteal_shadowweaver);
 *  - всё резолвится через Registry (без deprecated valueOf);
 *  - каталог дефолтов в коде, конфиг vfx.* только переопределяет.
 */
public final class FxService {

    private static final Map<String, String[]> DEFAULTS = new HashMap<>();

    static {
        // Каст абилок (слоты 1–6)
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
        // Каст активок спеков
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
        // 1.5.0 / Пакет 3: проки пассивок
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

    /**
     * VFX прока пассивки/спека: партикл + звук + (если включено) actionbar-тег.
     * Ключ procId ищется как vfx.proc.<procId>.{cast-sound,cast-particle,tag},
     * при отсутствии ключа — дефолт из DEFAULTS (ключ "proc.<procId>").
     */
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

    /** Legacy-обёртка для обратной совместимости. */
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

    /** Безопасный резолв звука через Registry.SOUNDS. */
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

    /** Безопасный резолв партикла через Registry.PARTICLE_TYPE. */
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
}

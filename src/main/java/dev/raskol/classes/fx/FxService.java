// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.fx;

import dev.raskol.classes.RaskolClasses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Движок звука/партиклов (1.5.0, Пакет 1).
 * FIX 1.5.0.1: каталог дефолтов вшит в код — звуки/партиклы работают
 * БЕЗ секции vfx в конфиге; конфиг (vfx.<id>.cast-sound/cast-particle)
 * теперь только ПЕРЕОПРЕДЕЛЯЕТ дефолты. Опечатка в конфиге = дефолт, не тишина.
 *
 *  - onCast — безусловный VFX (активки спеков, слот 6);
 *  - onAttempt — эвристика «свежего каста» по кулдауну (классовые 1–5):
 *    VFX играет только если кд запущен в последние 300 мс (каст прошёл);
 *  - proc — фидбек проков пассивок (Пакет 3).
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
        DEFAULTS.put("pw_shield", new String[]{"ITEM_SHIELD_BLOCK", "ENCHANT"});
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
        DEFAULTS.put("arcane_flow", new String[]{"BLOCK_ENCHANTMENT_TABLE_USE", "ENCHANT"});
        DEFAULTS.put("ice_ring", new String[]{"ENTITY_PLAYER_HURT_FREEZE", "SNOWFLAKE"});
        DEFAULTS.put("garrote", new String[]{"ENTITY_PLAYER_ATTACK_WEAK", "DAMAGE_INDICATOR"});
        DEFAULTS.put("smoke_bomb", new String[]{"BLOCK_FIRE_EXTINGUISH", "SMOKE"});
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
                return; // каст не прошёл (кд/ресурс/анлок) — без VFX
            }
        }
        castVfx(player, abilityId);
    }

    private void castVfx(Player player, String id) {
        FileConfiguration cfg = plugin.getConfig();
        String[] def = DEFAULTS.getOrDefault(id, new String[]{"", ""});
        // конфиг ПЕРЕОПРЕДЕЛЯЕТ дефолт; нет ключа или опечатка → дефолт/тишина
        String soundKey = cfg.getString("vfx." + id + ".cast-sound", def[0]);
        String particleKey = cfg.getString("vfx." + id + ".cast-particle", def[1]);

        Sound sound = resolveSound(soundKey);
        if (sound != null) {
            playSound(player.getLocation(), sound, 0.6f, 1.0f);
        }
        Particle particle = resolveParticle(particleKey);
        if (particle != null) {
            player.spawnParticle(particle,
                    player.getLocation().add(0.0, 1.0, 0.0),
                    12, 0.3, 0.4, 0.3, 0.0);
        }
    }

    /** Фидбек прока пассивки (Пакет 3): партикл + звук + опц. actionbar-тег. */
    public void proc(Player player, String tag, Particle particle, Sound sound) {
        if (particle != null) {
            player.spawnParticle(particle,
                    player.getLocation().add(0.0, 1.0, 0.0),
                    6, 0.3, 0.3, 0.3, 0.0);
        }
        if (sound != null) {
            playSound(player.getLocation(), sound, 0.4f, 1.2f);
        }
        if (tag != null && !tag.isEmpty()
                && plugin.getConfig().getBoolean("vfx.proc-actionbar", false)) {
            player.sendActionBar(Component.text(tag, NamedTextColor.YELLOW));
        }
    }

    /** Звук в точке (громкость затухает с дистанцией, ~16 блоков). */
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
        try {
            return Sound.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Безопасный резолв партикла: опечатка = null. */
    public Particle resolveParticle(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        try {
            return Particle.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

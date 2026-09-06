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

import java.util.Locale;

/**
 * Движок звука/партиклов (1.5.0, Пакет 1).
 * Единая точка всех VFX/SFX: каст / прок / трейлы.
 *  - звуки играют с ограниченной громкостью (слышно в ~16 блоках);
 *  - имена звуков/партиклов — из конфига vfx.<id>.*, резолвятся безопасно:
 *    опечатка = тишина, не краш;
 *  - onAttempt — эвристика «свежего каста» по кулдауну (классовые абилки 1–5),
 *    onCast — безусловный VFX (активки спеков, где успех известен).
 */
public final class FxService {

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
        Sound sound = resolveSound(cfg.getString("vfx." + id + ".cast-sound", ""));
        if (sound != null) {
            playSound(player.getLocation(), sound, 0.6f, 1.0f);
        }
        Particle particle = resolveParticle(cfg.getString("vfx." + id + ".cast-particle", ""));
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

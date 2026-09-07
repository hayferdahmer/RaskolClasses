// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 1.6.2: справедливость боя — фракционно-осознанные таргеты.
 * Союзники: сам кастер или игроки одной НЕПУСТОЙ фракции (FactionHook:
 * короны/Towny). Безфракционные между собой — НЕ союзники (PvP работает).
 * Гейт combat.friendly-fire (дефолт false):
 *  - false: наш урон не задевает союзников, хилы/щиты не ложатся на врагов;
 *  - true : старое поведение (AoE бьёт всех, хилы на кого угодно) для арен.
 */
public final class Targeting {

    private Targeting() {
    }

    /** Гейт дружественного огня из конфига. */
    public static boolean friendlyFire(RaskolClasses plugin) {
        return plugin.getConfig().getBoolean("combat.friendly-fire", false);
    }

    /** Союзники ли a и b (сам себе — всегда союзник). */
    public static boolean isAlly(RaskolClasses plugin, UUID a, UUID b) {
        if (a.equals(b)) {
            return true;
        }
        String fa = plugin.getFactionHook().factionOf(a);
        String fb = plugin.getFactionHook().factionOf(b);
        return !fa.isEmpty() && fa.equals(fb);
    }

    /** Может ли кастер нанести урон игроку-цели (фракции + гейт). */
    public static boolean canHitPlayer(RaskolClasses plugin, Player caster, Player target) {
        if (friendlyFire(plugin)) {
            return true;
        }
        return !isAlly(plugin, caster.getUniqueId(), target.getUniqueId());
    }

    /** Может ли кастер лечить/щитовать игрока-цель (себя/союзника; гейт открывает всех). */
    public static boolean canHealPlayer(RaskolClasses plugin, Player caster, Player target) {
        if (friendlyFire(plugin)) {
            return true;
        }
        return isAlly(plugin, caster.getUniqueId(), target.getUniqueId());
    }

    /** Валидная боевая цель: любой моб или игрок, проходящий canHitPlayer. */
    public static boolean isValidDamageTarget(RaskolClasses plugin, Player caster, LivingEntity target) {
        if (target instanceof Player tp) {
            return canHitPlayer(plugin, caster, tp);
        }
        return true; // мобы валидны всегда
    }
}

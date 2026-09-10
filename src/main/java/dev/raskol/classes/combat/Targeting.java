// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * 1.6.2: справедливость боя — фракционно-осознанные таргеты.
 * Союзники: сам кастер или игроки одной НЕПУСТОЙ фракции (FactionHook).
 * Гейт combat.friendly-fire (дефолт false).
 *
 * 1.6.11: hasLineOfSight — LOS-проверка AoE сквозь стены (гейт combat.aoe-los,
 * дефолт true): луч из глаз кастера в глаза цели, любой НЕпроходимый блок
 * (камень, стекло, двери) на пути = урон не проходит; вода/воздух/трава
 * (passable) не блокируют. Хилящие AoE проверкой не ограничены намеренно.
 *
 * 1.7.5-prep: либерализация canHealPlayer — бесфракционные игроки не считаются
 * врагами для целей лечения (согласовано с PriestAbilities.isAllyOrSelf 1.7.4).
 * Для canHitPlayer (PvP-урон) логика строгая: бесфракционные = враги,
 * что соответствует PvP-дизайну сервера.
 */
public final class Targeting {

    private Targeting() {
    }

    /** Гейт дружественного огня из конфига. */
    public static boolean friendlyFire(RaskolClasses plugin) {
        return plugin.getConfig().getBoolean("combat.friendly-fire", false);
    }

    /**
     * Союзники ли a и b (сам себе — всегда союзник).
     * Логика строгая: союзники = одна непустая фракция. Бесфракционные = не союзники.
     * Используется для PvP-урона (canHitPlayer).
     */
    public static boolean isAlly(RaskolClasses plugin, UUID a, UUID b) {
        if (a.equals(b)) {
            return true;
        }
        String fa = plugin.getFactionHook().factionOf(a);
        String fb = plugin.getFactionHook().factionOf(b);
        return !fa.isEmpty() && fa.equals(fb);
    }

    /**
     * Может ли кастер нанести урон игроку-цели (фракции + гейт).
     * Бесфракционные игроки считаются врагами (PvP-дизайн).
     */
    public static boolean canHitPlayer(RaskolClasses plugin, Player caster, Player target) {
        if (friendlyFire(plugin)) {
            return true;
        }
        return !isAlly(plugin, caster.getUniqueId(), target.getUniqueId());
    }

    /**
     * Может ли кастер лечить/щитовать игрока-цель.
     * 1.7.5-prep: ЛИЕБРАЛЬНАЯ логика — если фракции пусты, цель считается
     * валидной для хила (бесфракционный ≠ враг для лечения). Согласовано с
     * PriestAbilities.isAllyOrSelf (1.7.4) для будущих хилок через этот API.
     */
    public static boolean canHealPlayer(RaskolClasses plugin, Player caster, Player target) {
        if (caster.getUniqueId().equals(target.getUniqueId())) {
            return true;
        }
        if (friendlyFire(plugin)) {
            return true;
        }
        String fa = plugin.getFactionHook().factionOf(caster.getUniqueId());
        String fb = plugin.getFactionHook().factionOf(target.getUniqueId());
        // Если обе фракции пусты — считаем цель валидной для хила
        if (fa.isEmpty() && fb.isEmpty()) {
            return true;
        }
        // Если хотя бы одна непустая — союзники только при совпадении
        return !fa.isEmpty() && fa.equals(fb);
    }

    /** Валидная боевая цель: любой моб или игрок, проходящий canHitPlayer. */
    public static boolean isValidDamageTarget(RaskolClasses plugin, Player caster, LivingEntity target) {
        if (target instanceof Player tp) {
            return canHitPlayer(plugin, caster, tp);
        }
        return true; // мобы валидны всегда
    }

    /**
     * 1.6.11: прямая видимость между существами для AoE-урона.
     * Гейт combat.aoe-los=false отключает проверку целиком (старое поведение).
     * Разные миры = нет видимости. Луч короче эпсилон = видимость (та же точка).
     */
    public static boolean hasLineOfSight(RaskolClasses plugin, LivingEntity from, LivingEntity to) {
        if (!plugin.getConfig().getBoolean("combat.aoe-los", true)) {
            return true;
        }
        if (from == null || to == null || from.getWorld() == null
                || !from.getWorld().equals(to.getWorld())) {
            return false;
        }
        Location fromLoc = from.getEyeLocation();
        Location toLoc = to.getEyeLocation();
        Vector dir = toLoc.toVector().subtract(fromLoc.toVector());
        double dist = dir.length();
        if (dist < 1e-6) {
            return true;
        }
        dir.normalize();
        RayTraceResult hit = from.getWorld().rayTraceBlocks(fromLoc, dir, dist,
                FluidCollisionMode.NEVER, true);
        return hit == null;
    }
}

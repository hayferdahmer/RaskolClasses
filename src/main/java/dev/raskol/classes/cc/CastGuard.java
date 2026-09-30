// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.cc;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.entity.Player;

/**
 * 1.13.0 (Батч 2): проверка возможности каста способности под CC.
 *
 * STUN (Оцепенение): полный запрет кастов.
 * SILENCE (Немота): запрет кастов (кроме instant-способностей, если помечены).
 * FEAR (Ужас): запрет кастов.
 *
 * Интеграция: AbilityRegistry.tryCast() вызывает canCast() перед выполнением;
 * если false — отмена с сообщением "§cКаст невозможен: {type}" (Батч 3).
 *
 * interruptCast (прерывание активного каста при наложении CC) — Батч 3
 * (требует знания структуры AbilityRegistry.channeling-state).
 */
public final class CastGuard {

    private final RaskolClasses plugin;

    public CastGuard(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /**
     * Проверка: может ли игрок кастовать способность прямо сейчас?
     * @param player кастер
     * @param isInstant true для мгновенных способностей (не прерываются SILENCE)
     * @return true если каст разрешён, false если заблокирован CC
     */
    public boolean canCast(Player player, boolean isInstant) {
        CCService cc = plugin.getCC();
        if (cc.has(player.getUniqueId(), CCType.STUN)) {
            return false;
        }
        if (cc.has(player.getUniqueId(), CCType.FEAR)) {
            return false;
        }
        if (cc.has(player.getUniqueId(), CCType.SILENCE) && !isInstant) {
            return false;
        }
        return true;
    }

    /**
     * Причина блокировки каста (для сообщений игроку).
     * @return CCType который блокирует, или null если каст разрешён
     */
    public CCType blockReason(Player player, boolean isInstant) {
        CCService cc = plugin.getCC();
        if (cc.has(player.getUniqueId(), CCType.STUN)) {
            return CCType.STUN;
        }
        if (cc.has(player.getUniqueId(), CCType.FEAR)) {
            return CCType.FEAR;
        }
        if (cc.has(player.getUniqueId(), CCType.SILENCE) && !isInstant) {
            return CCType.SILENCE;
        }
        return null;
    }
}

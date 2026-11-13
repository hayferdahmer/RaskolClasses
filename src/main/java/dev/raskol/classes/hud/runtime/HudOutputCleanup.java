// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import java.util.UUID;

/**
 * Порт очистки presentation-ресурсов, принадлежащих RaskolHUD.
 *
 * <p>Реализация удаляет только собственные ActionBar/BossBar/Scoreboard
 * handles и не затрагивает вывод других плагинов.</p>
 */
public interface HudOutputCleanup {

    /** Очищает все HUD-owned output конкретного игрока. */
    void clearPlayer(UUID playerId);

    /** Очищает все HUD-owned output всех игроков. */
    void clearAll();
}

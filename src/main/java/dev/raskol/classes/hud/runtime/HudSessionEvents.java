// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Порт событий жизненного цикла игровой сессии, необходимых HUD.
 *
 * <p>Первая версия предоставляет только quit-событие: HUD очищает
 * принадлежащие ему per-player cache и presentation handles. Bukkit-типы
 * намеренно не выходят за пределы адаптера.</p>
 */
public interface HudSessionEvents {

    /**
     * Регистрирует обработчик выхода игрока.
     *
     * @param handler обработчик UUID игрока
     * @return handle для снятия регистрации
     */
    HudRegistration onQuit(Consumer<UUID> handler);
}

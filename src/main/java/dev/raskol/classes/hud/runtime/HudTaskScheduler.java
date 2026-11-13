// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/**
 * Узкий порт планировщика задач RaskolHUD.
 *
 * <p>Реализация должна выполнять callback на основном потоке сервера.
 * Интерфейс не раскрывает Bukkit API, поэтому runtime coordinator можно
 * проверять на детерминированном тестовом планировщике.</p>
 */
public interface HudTaskScheduler {

    /**
     * Планирует повторяющуюся задачу.
     *
     * @param action действие одного цикла обновления
     * @param initialDelayTicks задержка до первого запуска, неотрицательная
     * @param periodTicks период повторения, положительный
     * @return handle, принадлежащий вызывающему HUD runtime owner
     */
    TaskHandle scheduleRepeating(Runnable action, long initialDelayTicks, long periodTicks);
}

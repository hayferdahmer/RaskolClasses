// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.runtime;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Граница планирования одноразовых callback без зависимости от Bukkit API. */
@FunctionalInterface
public interface PlayerCallbackScheduler {

    /** Планирует callback через указанное число тиков и возвращает отменяемый handle. */
    TaskHandle schedule(long delayTicks, Runnable callback);
}

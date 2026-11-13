// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
 // SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.flavor.runtime;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Планировщик периодической ауры CrownFlavorService. */
public interface FlavorTaskScheduler {

    /** Планирует aura tick с исходным интервалом 40 тиков. */
    TaskHandle scheduleAura();
}

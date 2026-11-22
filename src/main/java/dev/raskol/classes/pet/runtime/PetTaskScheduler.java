// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.pet.runtime;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Планировщик двух задач, принадлежащих runtime петов. */
public interface PetTaskScheduler {

    /** Планирует периодическое обновление петов. */
    TaskHandle scheduleTickTask();

    /** Планирует однократное сканирование осиротевших петов. */
    TaskHandle scheduleOrphanScan();
}

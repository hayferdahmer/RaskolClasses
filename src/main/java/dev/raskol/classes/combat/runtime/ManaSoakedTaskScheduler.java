// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.runtime;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Планировщик периодического цикла пассивного резиста ManaSoaked. */
public interface ManaSoakedTaskScheduler {

    /** Планирует один периодический цикл ManaSoaked. */
    TaskHandle scheduleTick();
}

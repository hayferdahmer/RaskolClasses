// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hotbar.runtime;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Планировщик периодического обновления индикаторов кулдауна свитков. */
public interface ScrollCooldownTaskScheduler {

    /** Планирует один цикл обновления индикаторов. */
    TaskHandle scheduleTick();
}

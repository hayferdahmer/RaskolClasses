// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Порт синхронного периодического HP tick. */
public interface HpBarTaskScheduler {
    TaskHandle schedule(Runnable tick, long periodTicks);
}

// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.dot.runtime;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Планировщик периодического producer DoT; состояние эффектов ему не принадлежит. */
public interface DotTaskScheduler {
    TaskHandle scheduleTick(Runnable tick);
}

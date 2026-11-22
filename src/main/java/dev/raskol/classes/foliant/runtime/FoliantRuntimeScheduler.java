// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.foliant.runtime;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Порт периодического сброса dirty-состояния Фолианта. */
public interface FoliantRuntimeScheduler {

    TaskHandle scheduleLockFlush(long periodTicks);
}

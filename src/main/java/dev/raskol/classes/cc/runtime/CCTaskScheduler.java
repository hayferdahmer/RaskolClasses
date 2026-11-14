// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-RaskOL-Proprietary-2.0
package dev.raskol.classes.cc.runtime;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Планировщик частого producer CC; активные эффекты и DR-состояние ему не принадлежат. */
public interface CCTaskScheduler {
    TaskHandle scheduleTick(Runnable tick);
}

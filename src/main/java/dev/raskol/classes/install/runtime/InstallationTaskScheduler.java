// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.install.runtime;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Планировщик периодического sweep инсталляций. */
public interface InstallationTaskScheduler {

    /** Планирует один цикл проверки активных инсталляций. */
    TaskHandle scheduleSweep();
}

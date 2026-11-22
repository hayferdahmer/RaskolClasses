// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.runtime;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Порт планирования cross-storage autosave producer. */
public interface AutosaveTaskScheduler {
    TaskHandle scheduleAutosave();
}

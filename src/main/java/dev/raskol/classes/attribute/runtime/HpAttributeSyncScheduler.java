// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.attribute.runtime;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Порт периодической синхронизации атрибута MAX_HEALTH. */
public interface HpAttributeSyncScheduler {

    TaskHandle scheduleSweep(long periodTicks);
}

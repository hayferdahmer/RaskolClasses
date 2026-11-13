// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.domain;

/** Безопасная категория причины, по которой актуальное состояние цели неизвестно. */
public enum TargetUnavailableReason {
    SESSION_UNAVAILABLE,
    TARGET_RESOLUTION_FAILED,
    POLICY_PROVIDER_UNAVAILABLE
}

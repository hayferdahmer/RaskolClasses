// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.application;

/** Безопасная причина отклонения команды выбора цели. */
public enum TargetSelectionRejectionReason {
    SELF_TARGET,
    OFF_HAND_NOT_SUPPORTED,
    UNSUPPORTED_SOURCE,
    POLICY_REJECTED
}

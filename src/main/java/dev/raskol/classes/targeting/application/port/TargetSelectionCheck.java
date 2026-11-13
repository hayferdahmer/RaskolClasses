// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.application.port;

import dev.raskol.classes.targeting.application.TargetInteractionInput;
import dev.raskol.classes.targeting.application.TargetSelectionPolicyResult;

/** Один независимый этап проверки допуска к выбору цели. */
@FunctionalInterface
public interface TargetSelectionCheck {

    TargetSelectionPolicyResult evaluate(TargetInteractionInput input);
}

// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.application.port;

import dev.raskol.classes.targeting.application.TargetInteractionInput;
import dev.raskol.classes.targeting.application.TargetSelectionPolicyResult;

/** Порт композиции actor, candidate, range, permission, PvP, territory и NPC-проверок. */
@FunctionalInterface
public interface TargetSelectionPolicy {

    TargetSelectionPolicyResult evaluate(TargetInteractionInput input);
}

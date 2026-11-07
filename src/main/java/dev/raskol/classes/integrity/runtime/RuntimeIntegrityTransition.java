// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.runtime;

import java.time.Instant;
import java.util.Objects;

public record RuntimeIntegrityTransition(
        Instant occurredAt, RuntimeIntegrityState previousState, RuntimeIntegrityState newState, String reason) {

    public RuntimeIntegrityTransition {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(previousState, "previousState");
        Objects.requireNonNull(newState, "newState");
        Objects.requireNonNull(reason, "reason");

        reason = reason.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');

        if (reason.length() > 512) {
            reason = reason.substring(0, 512);
        }
    }
}

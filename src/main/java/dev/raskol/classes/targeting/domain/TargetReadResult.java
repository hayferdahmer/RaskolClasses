// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.domain;

import java.util.Objects;

/** Типизированный результат чтения locked target без смешения NONE и UNAVAILABLE. */
public sealed interface TargetReadResult
        permits TargetReadResult.None, TargetReadResult.Available, TargetReadResult.Unavailable {

    /** Запрос выполнен успешно, выбранной цели нет. */
    record None() implements TargetReadResult {}

    /** Выбранная цель и её актуальный снимок доступны. */
    record Available(TargetView view) implements TargetReadResult {
        public Available {
            Objects.requireNonNull(view, "view");
        }
    }

    /** Источник временно не может подтвердить актуальное состояние. */
    record Unavailable(TargetUnavailableReason reason) implements TargetReadResult {
        public Unavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }

    static TargetReadResult none() {
        return new None();
    }

    static TargetReadResult available(TargetView view) {
        return new Available(view);
    }

    static TargetReadResult unavailable(TargetUnavailableReason reason) {
        return new Unavailable(reason);
    }
}

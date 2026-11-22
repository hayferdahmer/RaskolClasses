// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.snapshot;

import java.util.Objects;
import java.util.Optional;

/** Значение read-модели, в котором отсутствие данных не маскируется нулём. */
public record HudValue<T>(DataStatus status, T value) {

    public HudValue {
        Objects.requireNonNull(status, "status");
        if (status == DataStatus.AVAILABLE && value == null) {
            throw new IllegalArgumentException("Доступное значение HUD не может быть null");
        }
        if (status != DataStatus.AVAILABLE && value != null) {
            throw new IllegalArgumentException("Недоступное значение HUD не должно содержать payload");
        }
    }

    public static <T> HudValue<T> available(T value) {
        return new HudValue<>(DataStatus.AVAILABLE, Objects.requireNonNull(value, "value"));
    }

    public static <T> HudValue<T> notApplicable() {
        return new HudValue<>(DataStatus.NOT_APPLICABLE, null);
    }

    public static <T> HudValue<T> unavailable() {
        return new HudValue<>(DataStatus.UNAVAILABLE, null);
    }

    public Optional<T> optionalValue() {
        return Optional.ofNullable(value);
    }
}

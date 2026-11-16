// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.snapshot;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Неизменяемое намерение виджета для последующего channel arbitration. */
public record WidgetFrame(
        String widgetId,
        String channel,
        int priority,
        String payload,
        OptionalLong expiresAtMillis,
        Optional<BossBarPresentation> bossBarPresentation) {

    public WidgetFrame(String widgetId, String channel, int priority, String payload, OptionalLong expiresAtMillis) {
        this(widgetId, channel, priority, payload, expiresAtMillis, Optional.empty());
    }

    public WidgetFrame {
        requireText(widgetId, "widgetId");
        requireText(channel, "channel");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(expiresAtMillis, "expiresAtMillis");
        bossBarPresentation = Objects.requireNonNull(bossBarPresentation, "bossBarPresentation");
        if (expiresAtMillis.isPresent() && expiresAtMillis.getAsLong() < 0) {
            throw new IllegalArgumentException("expiresAtMillis не может быть отрицательным");
        }
        if (bossBarPresentation.isPresent() && !"BOSS_BAR".equals(channel)) {
            throw new IllegalArgumentException("BossBar presentation допустим только для канала BOSS_BAR");
        }
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " не может быть пустым");
        }
    }
}

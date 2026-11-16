// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.snapshot;

import java.util.Objects;
import java.util.Set;

/** Типизированные параметры BossBar, не зависящие от Bukkit/Paper API. */
public record BossBarPresentation(double progress, Color color, Style style, Set<Flag> flags) {

    public BossBarPresentation {
        if (!Double.isFinite(progress) || progress < 0.0 || progress > 1.0) {
            throw new IllegalArgumentException("BossBar progress должен быть в диапазоне [0.0, 1.0]");
        }
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(style, "style");
        flags = Set.copyOf(Objects.requireNonNull(flags, "flags"));
    }

    public enum Color {
        PINK,
        BLUE,
        RED,
        GREEN,
        YELLOW,
        PURPLE,
        WHITE
    }

    public enum Style {
        SOLID,
        SEGMENTED_6,
        SEGMENTED_10,
        SEGMENTED_12,
        SEGMENTED_20
    }

    public enum Flag {
        DARKEN_SKY,
        PLAY_BOSS_MUSIC,
        CREATE_FOG
    }
}

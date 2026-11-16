// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.snapshot;

import java.util.Optional;

/** Виджет читает только immutable snapshot и возвращает presentation intent без Bukkit API. */
public interface HudWidget {
    String id();

    Optional<WidgetFrame> evaluate(PlayerHudSnapshot snapshot);
}

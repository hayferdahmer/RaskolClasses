// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.snapshot;

import java.util.List;
import java.util.UUID;

/** Порт публикации готовых кадров; реализация Paper появится в отдельном slice. */
public interface HudFramePublisher {
    void publish(UUID playerId, PlayerHudSnapshot snapshot, List<WidgetFrame> frames);

    void clearPlayer(UUID playerId);
}

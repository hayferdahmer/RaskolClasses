// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.snapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Реестр с уникальными ID и детерминированным порядком вычисления виджетов. */
public final class HudWidgetRegistry {
    private final List<HudWidget> widgets = new ArrayList<>();

    public synchronized void register(HudWidget widget) {
        Objects.requireNonNull(widget, "widget");
        String id = requireId(widget.id());
        if (widgets.stream().anyMatch(existing -> existing.id().equals(id))) {
            throw new IllegalArgumentException("HUD-виджет уже зарегистрирован: " + id);
        }
        widgets.add(widget);
        widgets.sort(Comparator.comparing(HudWidget::id));
    }

    public synchronized List<HudWidget> widgets() {
        return List.copyOf(widgets);
    }

    private static String requireId(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("ID HUD-виджета не может быть пустым");
        }
        return id;
    }
}

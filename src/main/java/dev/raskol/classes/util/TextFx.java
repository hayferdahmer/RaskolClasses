// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/** Текстовые эффекты Adventure: посимвольный градиент между двумя цветами. */
public final class TextFx {

    private TextFx() {}

    /** Градиент from → to по каждому символу (lerp Adventure API). */
    public static Component gradient(String text, TextColor from, TextColor to) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        Component result = Component.empty();
        int last = text.length() - 1;
        for (int i = 0; i < text.length(); i++) {
            float t = last == 0 ? 0f : (float) i / last;
            result = result.append(Component.text(text.charAt(i)).color(TextColor.lerp(t, from, to)));
        }
        return result;
    }
}

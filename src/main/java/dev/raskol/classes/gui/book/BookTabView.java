// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.gui.book;

import dev.raskol.classes.gui.ClassBook;

/**
 * 1.11.4 (P4b): контракт вкладки Книги. Одна вкладка = один файл.
 * render — расстановка предметов; onClick — обработка кликов таба
 * (информационные вкладки оставляют дефолт-пустой onClick).
 */
public interface BookTabView {

    ClassBook.Tab id();

    void render(RenderCtx ctx);

    default void onClick(RenderCtx ctx, int slot, boolean left, boolean right) {
    }
}

// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap;

/**
 * Результат перезагрузки плагина.
 *
 * Ошибка не означает, что все ранее применённые изменения автоматически отменены.
 */
public record PluginReloadResult(boolean successful, String message) {

    public PluginReloadResult {
        message = message == null ? "" : message;
    }

    public static PluginReloadResult success() {
        return new PluginReloadResult(true, "");
    }

    public static PluginReloadResult itemsReload(boolean successful) {
        return successful
                ? success()
                : failure("предметы и крафт", "items.yml или crafting.yml не удалось загрузить");
    }

    public static PluginReloadResult failure(String stage, String detail) {
        String reason = detail == null || detail.isBlank() ? "причина не указана" : detail;
        return new PluginReloadResult(
                false,
                "Этап «" + stage + "»: " + reason + ". Часть настроек могла примениться; автоматического отката нет.");
    }
}

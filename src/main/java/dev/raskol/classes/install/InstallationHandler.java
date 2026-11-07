// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.install;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 1.11.4 (P2): контракт типа инсталляции. Один тип = один файл.
 * Реестр (InstallationService) ведёт гейты/лимиты/TTL; обработчик — визуал,
 * звуки, тики и собственные задачи.
 */
public interface InstallationHandler {

    InstallationType type();

    /** Постановка: визуал/звуки/задачи. false = отказ (сообщение уже выдано). */
    boolean place(Player p, Location loc, UUID instId);

    /** Секундный тик, пока активна. true = держать, false = снять (сработала). */
    boolean tick(ActiveInstallation inst);

    /** Снятие/истечение: отмена задач, стоп эмбиента, снятие модификаторов. */
    void expire(ActiveInstallation inst);

    double radius();

    int cooldownSeconds();

    int durationSeconds();
}

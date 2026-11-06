// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.install;

import org.bukkit.Location;

import java.util.UUID;

/** 1.11.4 (P2): публичное представление активной инсталляции (реестр ↔ обработчики). */
public record ActiveInstallation(UUID id, InstallationType type, UUID owner,
                                 Location location, long expiresAt) {
}

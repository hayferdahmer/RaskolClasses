// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.install;

import org.bukkit.Location;

import java.util.UUID;

/** 1.11.4 (P2): публичное представление активной инсталляции (реестр ↔ обработчики). */
public record ActiveInstallation(UUID id, InstallationType type, UUID owner,
                                 Location location, long expiresAt) {
}

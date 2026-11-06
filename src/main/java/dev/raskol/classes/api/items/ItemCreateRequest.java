// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.api.items;

/** Request DTO for API consumers that need explicit deterministic generation. */
public record ItemCreateRequest(String itemId,int itemLevel,long seed) { }

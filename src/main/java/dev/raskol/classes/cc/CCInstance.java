// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.cc;

import java.util.UUID;

/**
 * 1.13.0: живой экземпляр CC на цели. Immutable по сути, срок жизни читается
 * по nowMillis. sourceUuid — атрибуция (кто наложил) для фидбека и статистики.
 */
public final class CCInstance {

    private final CCType type;
    private final UUID sourceUuid;
    private final long startedAt;
    private final long expiresAt;
    private final int appliedTicks;
    private final double drMultiplier;

    public CCInstance(CCType type, UUID sourceUuid, long startedAt,
                      int appliedTicks, double drMultiplier) {
        this.type = type;
        this.sourceUuid = sourceUuid;
        this.startedAt = startedAt;
        this.appliedTicks = appliedTicks;
        this.drMultiplier = drMultiplier;
        this.expiresAt = startedAt + appliedTicks * 50L;
    }

    public CCType type() { return type; }
    public UUID sourceUuid() { return sourceUuid; }
    public long startedAt() { return startedAt; }
    public long expiresAt() { return expiresAt; }
    public int appliedTicks() { return appliedTicks; }
    public double drMultiplier() { return drMultiplier; }

    public boolean expired(long nowMillis) {
        return nowMillis >= expiresAt;
    }

    public long remainingTicks(long nowMillis) {
        long left = expiresAt - nowMillis;
        return left <= 0L ? 0L : (left + 49L) / 50L;
    }
}

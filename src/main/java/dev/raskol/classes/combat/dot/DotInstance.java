// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat.dot;

import java.util.UUID;

/**
 * 1.12.4: живой экземпляр DoT на цели. Mutable: стеки и срок жизни меняются
 * при повторном наложении (refresh). Владелец — атрибуция урона и kill-кредита.
 */
public final class DotInstance {

    private final DotDef def;
    private final UUID ownerUuid;
    private int stacks;
    private long expiresAt;

    public DotInstance(DotDef def, UUID ownerUuid, long nowMillis) {
        this.def = def;
        this.ownerUuid = ownerUuid;
        this.stacks = 1;
        this.expiresAt = nowMillis + def.durationMillis();
    }

    public DotDef def() {
        return def;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public int stacks() {
        return stacks;
    }

    public long expiresAt() {
        return expiresAt;
    }

    /** Повторное наложение: +1 стек (до maxStacks) + освежение длительности. */
    public void refresh(long nowMillis) {
        if (stacks < def.maxStacks()) {
            stacks++;
        }
        expiresAt = nowMillis + def.durationMillis();
    }

    public boolean expired(long nowMillis) {
        return nowMillis >= expiresAt;
    }
}

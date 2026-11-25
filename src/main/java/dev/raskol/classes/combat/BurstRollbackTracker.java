// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Хранит откат burst-лимита по идентичности события, чтобы вложенный урон
 * не мог сбросить или подменить состояние внешнего события.
 *
 * Вызывается только из основного потока Bukkit.
 */
final class BurstRollbackTracker {

    private final Map<Object, Handle> entries = new IdentityHashMap<>();

    void arm(
            Object event,
            UUID playerId,
            BurstReservationLedger.Token token,
            double amount,
            double scale,
            long createdAtMillis) {
        if (event == null
                || playerId == null
                || token == null
                || !Double.isFinite(amount)
                || amount <= 0.0
                || !Double.isFinite(scale)
                || scale <= 0.0) {
            throw new IllegalArgumentException("Некорректные данные burst reservation handle");
        }
        if (entries.containsKey(event)) {
            throw new IllegalStateException("Для Paper damage event уже создан burst reservation handle");
        }
        entries.put(event, new Handle(playerId, token, amount, scale, createdAtMillis));
    }

    Handle take(Object event) {
        return event == null ? null : entries.remove(event);
    }

    List<Handle> expire(long nowMillis, long ttlMillis) {
        List<Handle> expired = new ArrayList<>();
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Handle handle = iterator.next().getValue();
            if (nowMillis - handle.createdAtMillis() >= ttlMillis) {
                iterator.remove();
                if (handle.finish(State.ROLLED_BACK)) {
                    expired.add(handle);
                }
            }
        }
        return expired;
    }

    int size() {
        return entries.size();
    }

    enum State {
        OPEN,
        SETTLED,
        ROLLED_BACK
    }

    static final class Handle {
        private final UUID playerId;
        private final BurstReservationLedger.Token token;
        private final double reservedAmount;
        private final double scale;
        private final long createdAtMillis;
        private State state = State.OPEN;

        private Handle(
                UUID playerId,
                BurstReservationLedger.Token token,
                double reservedAmount,
                double scale,
                long createdAtMillis) {
            this.playerId = playerId;
            this.token = token;
            this.reservedAmount = reservedAmount;
            this.scale = scale;
            this.createdAtMillis = createdAtMillis;
        }

        UUID playerId() {
            return playerId;
        }

        BurstReservationLedger.Token token() {
            return token;
        }

        double reservedAmount() {
            return reservedAmount;
        }

        double scale() {
            return scale;
        }

        long createdAtMillis() {
            return createdAtMillis;
        }

        State state() {
            return state;
        }

        boolean finish(State terminalState) {
            if (state != State.OPEN || terminalState == null || terminalState == State.OPEN) {
                return false;
            }
            state = terminalState;
            return true;
        }
    }
}

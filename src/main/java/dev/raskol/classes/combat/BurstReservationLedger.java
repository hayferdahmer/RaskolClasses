// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Транзакционный журнал burst-бюджета с адресным откатом конкретной резервации.
 * Все операции выполняются под внешней блокировкой владельца журнала.
 */
final class BurstReservationLedger {

    private static final double EPSILON = 1.0e-9;

    private final Deque<Token> entries = new ArrayDeque<>();

    Allocation reserve(long nowMillis, long windowMillis, double cap, double requested) {
        prune(nowMillis, windowMillis);
        if (!Double.isFinite(requested) || requested <= 0.0) {
            return new Allocation(0.0, null);
        }
        if (!Double.isFinite(cap) || cap <= 0.0) {
            return new Allocation(requested, null);
        }

        double reserved = entries.stream().mapToDouble(Token::amount).sum();
        double allowed = Math.min(requested, Math.max(0.0, cap - reserved));
        if (allowed <= 0.0) {
            return new Allocation(0.0, null);
        }

        Token token = new Token(nowMillis, allowed);
        entries.addLast(token);
        return new Allocation(allowed, token);
    }

    void rollback(Token token, double amount) {
        if (token == null || !Double.isFinite(amount) || amount <= 0.0 || !entries.contains(token)) {
            return;
        }
        double rollback = Math.min(amount, token.amount);
        token.amount -= rollback;
        if (token.amount <= EPSILON) {
            entries.remove(token);
        }
    }

    /** Приводит конкретную резервацию к фактическому расходу, включая превышение резерва. */
    void reconcile(Token token, double actualAmount) {
        if (token == null || !Double.isFinite(actualAmount) || actualAmount < 0.0 || !entries.contains(token)) {
            return;
        }
        if (actualAmount <= EPSILON) {
            entries.remove(token);
            token.amount = 0.0;
            return;
        }
        // Фактический расход может быть выше исходного резерва после внешних Paper listeners.
        token.amount = actualAmount;
    }

    void prune(long nowMillis, long windowMillis) {
        while (!entries.isEmpty() && nowMillis - entries.peekFirst().createdAtMillis > windowMillis) {
            entries.removeFirst();
        }
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    record Allocation(double allowed, Token token) {}

    static final class Token {
        private final long createdAtMillis;
        private double amount;

        private Token(long createdAtMillis, double amount) {
            this.createdAtMillis = createdAtMillis;
            this.amount = amount;
        }

        private double amount() {
            return amount;
        }
    }
}

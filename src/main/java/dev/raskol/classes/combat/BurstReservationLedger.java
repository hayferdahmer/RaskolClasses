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

    Settlement settle(Token token, double finalAmount) {
        if (!Double.isFinite(finalAmount) || finalAmount < 0.0) {
            return new Settlement(false, true, token == null ? 0.0 : token.amount, finalAmount, 0.0);
        }
        if (token == null || !entries.contains(token)) {
            return new Settlement(false, false, 0.0, finalAmount, 0.0);
        }

        double previousAmount = token.amount;
        token.amount = finalAmount;
        if (token.amount <= EPSILON) {
            entries.remove(token);
        }
        return new Settlement(true, false, previousAmount, finalAmount, Math.max(0.0, finalAmount - previousAmount));
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

    void prune(long nowMillis, long windowMillis) {
        while (!entries.isEmpty() && nowMillis - entries.peekFirst().createdAtMillis > windowMillis) {
            entries.removeFirst();
        }
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    record Allocation(double allowed, Token token) {}

    record Settlement(boolean applied, boolean invalid, double previousAmount, double finalAmount, double overage) {}

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

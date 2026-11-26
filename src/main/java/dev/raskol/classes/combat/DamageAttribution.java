// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Учитывает изменение суммарного защитного пула (здоровье + поглощение),
 * произведённое вложенными вызовами CombatService. Каждый scope соответствует
 * одному синхронному вызову Entity.damage(...).
 */
final class DamageAttribution {

    private static final ThreadLocal<Deque<Frame>> FRAMES = new ThreadLocal<>();

    private DamageAttribution() {}

    static Scope begin(Object target, double healthBefore) {
        if (target == null || !Double.isFinite(healthBefore)) {
            throw new IllegalArgumentException("Target and finite initial health are required");
        }
        Deque<Frame> frames = FRAMES.get();
        if (frames == null) {
            frames = new ArrayDeque<>();
            FRAMES.set(frames);
        }
        Frame frame = new Frame(target, healthBefore);
        frames.push(frame);
        return new Scope(frame, frames);
    }

    static Scope begin(Object target, double healthBefore, double absorptionBefore) {
        return begin(target, defensivePool(healthBefore, absorptionBefore));
    }

    private static double defensivePool(double health, double absorption) {
        if (!Double.isFinite(health) || !Double.isFinite(absorption) || absorption < 0.0) {
            throw new IllegalArgumentException("Health and non-negative absorption must be finite");
        }
        double total = health + absorption;
        if (!Double.isFinite(total)) {
            throw new IllegalArgumentException("Defensive pool must be finite");
        }
        return total;
    }

    static final class Scope {
        private final Frame frame;
        private final Deque<Frame> frames;
        private boolean finished;

        private Scope(Frame frame, Deque<Frame> frames) {
            this.frame = frame;
            this.frames = frames;
        }

        /**
         * Возвращает только урон текущего вызова, исключая вложенные изменения HP.
         * Полный net health delta передаётся родительскому scope: он тоже наблюдает
         * этот delta в своём before/after измерении и должен исключить его целиком.
         */
        double finish(double healthAfter) {
            if (finished) {
                throw new IllegalStateException("Damage attribution scope is already finished");
            }
            if (!Double.isFinite(healthAfter)) {
                throw new IllegalArgumentException("Final health must be finite");
            }
            if (frames.peek() != frame) {
                throw new IllegalStateException("Damage attribution scopes must finish in LIFO order");
            }

            finished = true;
            frames.pop();

            double observedDamage = Math.max(0.0, frame.healthBefore - healthAfter);
            double ownDamage = Math.max(0.0, observedDamage - frame.nestedDamage);

            for (Frame parent : frames) {
                if (parent.target == frame.target) {
                    parent.nestedDamage += observedDamage;
                    break;
                }
            }
            if (frames.isEmpty()) {
                FRAMES.remove();
            }
            return ownDamage;
        }

        double finish(double healthAfter, double absorptionAfter) {
            return finish(defensivePool(healthAfter, absorptionAfter));
        }
    }

    private static final class Frame {
        private final Object target;
        private final double healthBefore;
        private double nestedDamage;

        private Frame(Object target, double healthBefore) {
            this.target = target;
            this.healthBefore = healthBefore;
        }
    }
}

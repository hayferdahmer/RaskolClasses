// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.build;

import java.util.UUID;

/**
 * 1.14.15: fallback-кошелёк на случай отсутствия PlayerPoints или ошибки
 * reflection. Все операции — no-op, balance всегда 0, take всегда false.
 *
 * Используется, чтобы raskolspec+ оставался работоспособен в dev-среде без
 * PlayerPoints: подписка не продаётся, но /rc plus status и чтение шаблонов
 * (для админа с правом raskolclasses.admin.plus) продолжают работать.
 */
public final class NullWallet implements ImperialWallet {

    @Override
    public int balance(UUID uuid) {
        return 0;
    }

    @Override
    public boolean take(UUID uuid, int amount) {
        return false;
    }

    @Override
    public void give(UUID uuid, int amount) {
        // no-op
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String implementationName() {
        return "null";
    }
}

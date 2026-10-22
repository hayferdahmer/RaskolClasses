// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.build;

import java.util.UUID;

/**
 * 1.14.15: абстракция кошелька «Империалы» (PlayerPoints) для raskolspec+.
 * RaskolClasses НЕ зависит напрямую от PlayerPoints API; реализация
 * PlayerPointsWallet подгружается по reflection, NullWallet — fallback
 * если плагина нет.
 *
 * Контракт:
 *  - balance(uuid): текущий баланс, 0 при ошибке;
 *  - take(uuid, amount): атомарно списать amount≥0; true если списано, false
 *    если не хватило или ошибка; amount==0 → true без списания;
 *  - give(uuid, amount): начислить; true только если кошелёк подтвердил запись;
 *  - isAvailable(): true если реализация рабочая.
 *
 * Все реализации обязаны быть потокобезопасными (PlayerPoints API сам по себе
 * thread-safe на запись; мы не кешируем баланс между вызовами).
 */
public interface ImperialWallet {

    int balance(UUID uuid);

    boolean take(UUID uuid, int amount);

    boolean give(UUID uuid, int amount);

    boolean isAvailable();

    /** Имя реализации для логов ("playerpoints" / "null"). */
    String implementationName();
}

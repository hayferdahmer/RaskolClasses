// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

import dev.raskol.classes.targeting.application.port.TargetSelectionPolicy;
import dev.raskol.classes.targeting.application.port.TargetSelectionStore;
import dev.raskol.classes.targeting.domain.TargetIdentity;
import dev.raskol.classes.targeting.domain.TargetInteractionHand;
import dev.raskol.classes.targeting.domain.TargetSelection;
import dev.raskol.classes.targeting.domain.TargetSelectionSource;

/** Оркестратор выбора цели; не зависит от Bukkit и не реализует игровые policy самостоятельно. */
public final class TargetingApplicationService {

    private final TargetSelectionStore selectionStore;
    private final TargetSelectionPolicy selectionPolicy;
    private final LongSupplier clock;

    public TargetingApplicationService(
            TargetSelectionStore selectionStore, TargetSelectionPolicy selectionPolicy, LongSupplier clock) {
        this.selectionStore = Objects.requireNonNull(selectionStore, "selectionStore");
        this.selectionPolicy = Objects.requireNonNull(selectionPolicy, "selectionPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Проверяет вход, применяет policy и атомарно заменяет selection для одного application owner.
     * Синхронизация не заменяет main-thread discipline Paper adapter; она защищает store от
     * конкурирующих вызовов этого экземпляра сервиса.
     */
    public synchronized TargetSelectionOutcome select(TargetInteractionInput input) {
        Objects.requireNonNull(input, "input");

        if (input.actorId().equals(input.candidateId())) {
            return new TargetSelectionOutcome.Rejected(TargetSelectionRejectionReason.SELF_TARGET);
        }
        if (input.hand() != TargetInteractionHand.MAIN_HAND) {
            return new TargetSelectionOutcome.Rejected(TargetSelectionRejectionReason.OFF_HAND_NOT_SUPPORTED);
        }
        if (input.source() != TargetSelectionSource.ENTITY_RIGHT_CLICK) {
            return new TargetSelectionOutcome.Rejected(TargetSelectionRejectionReason.UNSUPPORTED_SOURCE);
        }

        TargetSelectionPolicyResult policyResult =
                Objects.requireNonNull(selectionPolicy.evaluate(input), "selectionPolicy result");
        if (policyResult instanceof TargetSelectionPolicyResult.Rejected rejected) {
            return new TargetSelectionOutcome.Rejected(rejected.reason());
        }
        if (policyResult instanceof TargetSelectionPolicyResult.Unavailable unavailable) {
            return new TargetSelectionOutcome.Unavailable(unavailable.reason());
        }

        Optional<TargetSelection> current = selectionStore.findByOwner(input.actorId());
        TargetIdentity candidate = new TargetIdentity(input.candidateId());
        if (current.isPresent() && current.get().target().equals(candidate)) {
            return new TargetSelectionOutcome.AlreadySelected(current.get());
        }

        long selectedAtMillis = clock.getAsLong();
        if (selectedAtMillis < 0) {
            throw new IllegalStateException("Clock вернул отрицательное время");
        }

        TargetSelection selection =
                new TargetSelection(input.actorId(), candidate, selectedAtMillis, input.source());
        selectionStore.save(selection);
        return new TargetSelectionOutcome.Selected(selection);
    }

    /** Снимает выбранную цель владельца и возвращает предыдущее состояние, если оно было. */
    public synchronized Optional<TargetSelection> clear(UUID ownerId) {
        return selectionStore.remove(Objects.requireNonNull(ownerId, "ownerId"));
    }

    /** Возвращает текущее эфемерное состояние выбора без изменения его. */
    public synchronized Optional<TargetSelection> selectedTargetOf(UUID ownerId) {
        return selectionStore.findByOwner(Objects.requireNonNull(ownerId, "ownerId"));
    }

    /** Завершает targeting-состояние одной игровой сессии. */
    public synchronized void endSession(UUID ownerId) {
        selectionStore.remove(Objects.requireNonNull(ownerId, "ownerId"));
    }
}

package dev.raskol.classes.integrity.runtime;

import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeIntegrityStateManagerTest {

    @Test
    void initialStateShouldBeNotStarted() {
        RuntimeIntegrityStateManager manager =
                new RuntimeIntegrityStateManager();

        assertEquals(
                RuntimeIntegrityState.NOT_STARTED,
                manager.getSnapshot().state()
        );

        assertTrue(manager.getHistory().isEmpty());
    }

    @Test
    void beginCheckShouldMoveStateToRunning() {
        RuntimeIntegrityStateManager manager =
                new RuntimeIntegrityStateManager();

        assertTrue(manager.beginCheck());

        assertEquals(
                RuntimeIntegrityState.RUNNING,
                manager.getSnapshot().state()
        );
    }

    @Test
    void shouldNotStartAnotherCheckWhileRunning() {
        RuntimeIntegrityStateManager manager =
                new RuntimeIntegrityStateManager();

        assertTrue(manager.beginCheck());
        assertFalse(manager.beginCheck());
    }

    @Test
    void successfulCheckShouldResetConsecutiveFailures() {
        RuntimeIntegrityStateManager manager =
                new RuntimeIntegrityStateManager();

        manager.beginCheck();
        manager.failCheck(
                new IllegalStateException("First failure"),
                10
        );

        manager.beginCheck();
        RuntimeIntegritySnapshot snapshot = manager.completeCheck(
                result(IntegrityStatus.VERIFIED),
                20
        );

        assertEquals(
                RuntimeIntegrityState.VERIFIED,
                snapshot.state()
        );
        assertEquals(2, snapshot.totalChecks());
        assertEquals(0, snapshot.consecutiveFailures());
    }

    @Test
    void failedCheckShouldIncreaseFailureCounter() {
        RuntimeIntegrityStateManager manager =
                new RuntimeIntegrityStateManager();

        manager.beginCheck();

        RuntimeIntegritySnapshot snapshot = manager.completeCheck(
                result(IntegrityStatus.INVALID_SIGNATURE),
                15
        );

        assertEquals(
                RuntimeIntegrityState.FAILED,
                snapshot.state()
        );
        assertEquals(1, snapshot.totalChecks());
        assertEquals(1, snapshot.consecutiveFailures());
    }

    @Test
    void stopShouldBeTerminal() {
        RuntimeIntegrityStateManager manager =
                new RuntimeIntegrityStateManager();

        manager.beginCheck();
        manager.stop();

        assertEquals(
                RuntimeIntegrityState.STOPPED,
                manager.getSnapshot().state()
        );

        assertFalse(manager.beginCheck());

        assertEquals(
                RuntimeIntegrityState.STOPPED,
                manager.getSnapshot().state()
        );
    }

    @Test
    void completionAfterStopShouldNotChangeState() {
        RuntimeIntegrityStateManager manager =
                new RuntimeIntegrityStateManager();

        manager.beginCheck();
        manager.stop();

        RuntimeIntegritySnapshot snapshot = manager.completeCheck(
                result(IntegrityStatus.VERIFIED),
                10
        );

        assertEquals(
                RuntimeIntegrityState.STOPPED,
                snapshot.state()
        );
        assertEquals(0, snapshot.totalChecks());
    }

    @Test
    void failureAfterStopShouldNotChangeState() {
        RuntimeIntegrityStateManager manager =
                new RuntimeIntegrityStateManager();

        manager.beginCheck();
        manager.stop();

        RuntimeIntegritySnapshot snapshot = manager.failCheck(
                new IllegalStateException("Late failure"),
                10
        );

        assertEquals(
                RuntimeIntegrityState.STOPPED,
                snapshot.state()
        );
        assertEquals(0, snapshot.totalChecks());
    }

    @Test
    void historyShouldRecordStateTransitions() {
        RuntimeIntegrityStateManager manager =
                new RuntimeIntegrityStateManager();

        manager.beginCheck();
        manager.completeCheck(
                result(IntegrityStatus.VERIFIED),
                10
        );
        manager.stop();

        List<RuntimeIntegrityTransition> history = manager.getHistory();

        assertEquals(3, history.size());

        assertEquals(
                RuntimeIntegrityState.NOT_STARTED,
                history.get(0).previousState()
        );
        assertEquals(
                RuntimeIntegrityState.RUNNING,
                history.get(0).newState()
        );

        assertEquals(
                RuntimeIntegrityState.RUNNING,
                history.get(1).previousState()
        );
        assertEquals(
                RuntimeIntegrityState.VERIFIED,
                history.get(1).newState()
        );

        assertEquals(
                RuntimeIntegrityState.VERIFIED,
                history.get(2).previousState()
        );
        assertEquals(
                RuntimeIntegrityState.STOPPED,
                history.get(2).newState()
        );
    }

    @Test
    void historyShouldRespectConfiguredLimit() {
        RuntimeIntegrityStateManager manager =
                new RuntimeIntegrityStateManager(3);

        for (int i = 0; i < 5; i++) {
            manager.beginCheck();
            manager.completeCheck(
                    result(IntegrityStatus.VERIFIED),
                    1
            );
        }

        assertEquals(3, manager.getHistory().size());
    }

    @Test
    void historyShouldBeImmutable() {
        RuntimeIntegrityStateManager manager =
                new RuntimeIntegrityStateManager();

        manager.beginCheck();

        List<RuntimeIntegrityTransition> history =
                manager.getHistory();

        assertThrows(
                UnsupportedOperationException.class,
                history::clear
        );
    }

    @Test
    void invalidHistoryLimitShouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeIntegrityStateManager(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeIntegrityStateManager(-1)
        );
    }

    @Test
    void completionWithoutRunningCheckShouldBeRejected() {
        RuntimeIntegrityStateManager manager =
                new RuntimeIntegrityStateManager();

        assertThrows(
                IllegalStateException.class,
                () -> manager.completeCheck(
                        result(IntegrityStatus.VERIFIED),
                        10
                )
        );
    }

    @Test
    void negativeDurationShouldBeRejected() {
        RuntimeIntegrityStateManager manager =
                new RuntimeIntegrityStateManager();

        manager.beginCheck();

        assertThrows(
                IllegalArgumentException.class,
                () -> manager.completeCheck(
                        result(IntegrityStatus.VERIFIED),
                        -1
                )
        );
    }

    private static IntegrityVerificationResult result(
            IntegrityStatus status
    ) {
        return new IntegrityVerificationResult(
                status,
                "State manager test",
                1,
                List.of()
        );
    }
}

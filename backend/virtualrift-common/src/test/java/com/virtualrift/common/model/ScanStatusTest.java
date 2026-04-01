package com.virtualrift.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScanStatus Tests")
class ScanStatusTest {

    @Nested
    @DisplayName("Valid transitions")
    class ValidTransitions {

        @Test
        @DisplayName("PENDING can transition to RUNNING")
        void canTransition_pendingParaRunning_retornaTrue() {
            assertTrue(ScanStatus.PENDING.canTransitionTo(ScanStatus.RUNNING));
        }

        @Test
        @DisplayName("RUNNING can transition to COMPLETED")
        void canTransition_runningParaCompleted_retornaTrue() {
            assertTrue(ScanStatus.RUNNING.canTransitionTo(ScanStatus.COMPLETED));
        }

        @Test
        @DisplayName("RUNNING can transition to FAILED")
        void canTransition_runningParaFailed_retornaTrue() {
            assertTrue(ScanStatus.RUNNING.canTransitionTo(ScanStatus.FAILED));
        }

        @Test
        @DisplayName("RUNNING can transition to CANCELLED")
        void canTransition_runningParaCancelled_retornaTrue() {
            assertTrue(ScanStatus.RUNNING.canTransitionTo(ScanStatus.CANCELLED));
        }

        @Test
        @DisplayName("PENDING can transition to CANCELLED")
        void canTransition_pendingParaCancelled_retornaTrue() {
            assertTrue(ScanStatus.PENDING.canTransitionTo(ScanStatus.CANCELLED));
        }

        @Test
        @DisplayName("Same status should not be a valid transition")
        void canTransition_mesmoStatus_retornaFalse() {
            assertFalse(ScanStatus.PENDING.canTransitionTo(ScanStatus.PENDING));
        }
    }

    @Nested
    @DisplayName("Invalid transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("COMPLETED cannot transition to RUNNING")
        void canTransition_completedParaRunning_retornaFalse() {
            assertFalse(ScanStatus.COMPLETED.canTransitionTo(ScanStatus.RUNNING));
        }

        @Test
        @DisplayName("FAILED cannot transition to COMPLETED")
        void canTransition_failedParaCompleted_retornaFalse() {
            assertFalse(ScanStatus.FAILED.canTransitionTo(ScanStatus.COMPLETED));
        }

        @Test
        @DisplayName("CANCELLED cannot transition to RUNNING")
        void canTransition_cancelledParaRunning_retornaFalse() {
            assertFalse(ScanStatus.CANCELLED.canTransitionTo(ScanStatus.RUNNING));
        }

        @Test
        @DisplayName("PENDING cannot transition directly to COMPLETED")
        void canTransition_pendingParaCompleted_retornaFalse() {
            assertFalse(ScanStatus.PENDING.canTransitionTo(ScanStatus.COMPLETED));
        }

        @Test
        @DisplayName("PENDING cannot transition directly to FAILED")
        void canTransition_pendingParaFailed_retornaFalse() {
            assertFalse(ScanStatus.PENDING.canTransitionTo(ScanStatus.FAILED));
        }

        @Test
        @DisplayName("COMPLETED cannot transition to FAILED")
        void canTransition_completedParaFailed_retornaFalse() {
            assertFalse(ScanStatus.COMPLETED.canTransitionTo(ScanStatus.FAILED));
        }

        @Test
        @DisplayName("FAILED cannot transition to CANCELLED")
        void canTransition_failedParaCancelled_retornaFalse() {
            assertFalse(ScanStatus.FAILED.canTransitionTo(ScanStatus.CANCELLED));
        }

        @Test
        @DisplayName("CANCELLED cannot transition to COMPLETED")
        void canTransition_cancelledParaCompleted_retornaFalse() {
            assertFalse(ScanStatus.CANCELLED.canTransitionTo(ScanStatus.COMPLETED));
        }
    }

    @Nested
    @DisplayName("IsFinal")
    class IsFinal {

        @ParameterizedTest
        @MethodSource("finalStatuses")
        @DisplayName("should return true for final statuses")
        void isFinal_statusosFinais_retornaTrue(ScanStatus status) {
            assertTrue(status.isFinal());
        }

        @ParameterizedTest
        @EnumSource(value = ScanStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"COMPLETED", "FAILED", "CANCELLED"})
        @DisplayName("should return false for non-final statuses")
        void isFinal_statusosNaoFinais_retornaFalse(ScanStatus status) {
            assertFalse(status.isFinal());
        }

        private static Stream<ScanStatus> finalStatuses() {
            return Stream.of(ScanStatus.COMPLETED, ScanStatus.FAILED, ScanStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("IsInProgress")
    class IsInProgress {

        @Test
        @DisplayName("PENDING should be in progress")
        void isInProgress_quandoPending_retornaTrue() {
            assertTrue(ScanStatus.PENDING.isInProgress());
        }

        @Test
        @DisplayName("RUNNING should be in progress")
        void isInProgress_quandoRunning_retornaTrue() {
            assertTrue(ScanStatus.RUNNING.isInProgress());
        }

        @Test
        @DisplayName("COMPLETED should not be in progress")
        void isInProgress_quandoCompleted_retornaFalse() {
            assertFalse(ScanStatus.COMPLETED.isInProgress());
        }

        @Test
        @DisplayName("FAILED should not be in progress")
        void isInProgress_quandoFailed_retornaFalse() {
            assertFalse(ScanStatus.FAILED.isInProgress());
        }

        @Test
        @DisplayName("CANCELLED should not be in progress")
        void isInProgress_quandoCancelled_retornaFalse() {
            assertFalse(ScanStatus.CANCELLED.isInProgress());
        }
    }

    @Nested
    @DisplayName("FromString")
    class FromString {

        @ParameterizedTest
        @EnumSource(ScanStatus.class)
        @DisplayName("should parse from string (case-insensitive)")
        void fromString_quandoValido_retornaStatus(ScanStatus status) {
            ScanStatus result = ScanStatus.fromString(status.name());

            assertEquals(status, result);
        }

        @ParameterizedTest
        @EnumSource(ScanStatus.class)
        @DisplayName("should parse from lowercase string")
        void fromString_quandoMinusculo_retornaStatus(ScanStatus status) {
            ScanStatus result = ScanStatus.fromString(status.name().toLowerCase());

            assertEquals(status, result);
        }

        @Test
        @DisplayName("should throw when string is invalid")
        void fromString_quandoInvalido_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ScanStatus.fromString("INVALID")
            );

            assertTrue(exception.getMessage().contains("Invalid scan status"));
        }

        @Test
        @DisplayName("should throw when string is null")
        void fromString_quandoNulo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ScanStatus.fromString(null)
            );

            assertEquals("status cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Has five statuses")
    class HasFiveStatuses {

        @Test
        @DisplayName("should have exactly 5 statuses")
        void values_quandoChamado_retornaCincoStatus() {
            assertEquals(5, ScanStatus.values().length);
        }

        @Test
        @DisplayName("should contain all required statuses")
        void values_quandoChamado_contemTodosOsStatus() {
            ScanStatus[] statuses = ScanStatus.values();

            assertTrue(Stream.of(statuses).anyMatch(s -> s == ScanStatus.PENDING));
            assertTrue(Stream.of(statuses).anyMatch(s -> s == ScanStatus.RUNNING));
            assertTrue(Stream.of(statuses).anyMatch(s -> s == ScanStatus.COMPLETED));
            assertTrue(Stream.of(statuses).anyMatch(s -> s == ScanStatus.FAILED));
            assertTrue(Stream.of(statuses).anyMatch(s -> s == ScanStatus.CANCELLED));
        }
    }
}

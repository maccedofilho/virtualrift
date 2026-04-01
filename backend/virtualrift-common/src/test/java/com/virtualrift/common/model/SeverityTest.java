package com.virtualrift.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Severity Tests")
class SeverityTest {

    @Nested
    @DisplayName("Enum values")
    class EnumValues {

        @ParameterizedTest
        @EnumSource(Severity.class)
        @DisplayName("should have all severity levels")
        void severity_quandoChamado_retornaTodosOsNiveis(Severity severity) {
            assertNotNull(severity);
            assertNotNull(severity.name());
        }

        @Test
        @DisplayName("should have correct order from critical to info")
        void ordering_quandoComparado_retornaOrdemCorreta() {
            // Critical > High > Medium > Low > Info
            assertTrue(Severity.CRITICAL.compareTo(Severity.HIGH) > 0);
            assertTrue(Severity.HIGH.compareTo(Severity.MEDIUM) > 0);
            assertTrue(Severity.MEDIUM.compareTo(Severity.LOW) > 0);
            assertTrue(Severity.LOW.compareTo(Severity.INFO) > 0);

            // Verify ascending order for sorting
            Severity[] sorted = {Severity.INFO, Severity.LOW, Severity.MEDIUM, Severity.HIGH, Severity.CRITICAL};
            Severity[] actual = Severity.values();
            assertArrayEquals(sorted, actual);
        }

        @Test
        @DisplayName("should have exactly 5 severity levels")
        void values_quandoChamado_retornaCincoNiveis() {
            assertEquals(5, Severity.values().length);
        }
    }

    @Nested
    @DisplayName("FromString")
    class FromString {

        @ParameterizedTest
        @ValueSource(strings = {"CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"})
        @DisplayName("should parse from string (case-insensitive)")
        void fromString_quandoValido_retornaSeverity(String severity) {
            Severity result = Severity.fromString(severity);

            assertEquals(Severity.valueOf(severity), result);
        }

        @ParameterizedTest
        @ValueSource(strings = {"critical", "high", "medium", "low", "info"})
        @DisplayName("should parse from lowercase string")
        void fromString_quandoMinusculo_retornaSeverity(String severity) {
            Severity result = Severity.fromString(severity);

            assertNotNull(result);
            assertEquals(severity.toUpperCase(), result.name());
        }

        @Test
        @DisplayName("should throw when string is invalid")
        void fromString_quandoInvalido_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> Severity.fromString("INVALID")
            );

            assertTrue(exception.getMessage().contains("Invalid severity"));
        }

        @Test
        @DisplayName("should throw when string is null")
        void fromString_quandoNulo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> Severity.fromString(null)
            );

            assertEquals("severity cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Score")
    class Score {

        @Test
        @DisplayName("CRITICAL should have score 100")
        void score_critical_retornaCem() {
            assertEquals(100, Severity.CRITICAL.score());
        }

        @Test
        @DisplayName("HIGH should have score 75")
        void score_high_retornaSetentaCinco() {
            assertEquals(75, Severity.HIGH.score());
        }

        @Test
        @DisplayName("MEDIUM should have score 50")
        void score_medium_retornaCinquenta() {
            assertEquals(50, Severity.MEDIUM.score());
        }

        @Test
        @DisplayName("LOW should have score 25")
        void score_low_retornaVinteCinco() {
            assertEquals(25, Severity.LOW.score());
        }

        @Test
        @DisplayName("INFO should have score 0")
        void score_info_retornaZero() {
            assertEquals(0, Severity.INFO.score());
        }

        @Test
        @DisplayName("should be higher for more severe levels")
        void score_quandoComparado_severidadeMaiorTemScoreMaior() {
            assertTrue(Severity.CRITICAL.score() > Severity.HIGH.score());
            assertTrue(Severity.HIGH.score() > Severity.MEDIUM.score());
            assertTrue(Severity.MEDIUM.score() > Severity.LOW.score());
            assertTrue(Severity.LOW.score() > Severity.INFO.score());
        }
    }

    @Nested
    @DisplayName("IsCriticalOrHigher")
    class IsCriticalOrHigher {

        @Test
        @DisplayName("CRITICAL should be critical or higher")
        void isCriticalOrHigher_quandoCritical_retornaTrue() {
            assertTrue(Severity.CRITICAL.isCriticalOrHigher());
        }

        @Test
        @DisplayName("HIGH should not be critical or higher")
        void isCriticalOrHigher_quandoHigh_retornaFalse() {
            assertFalse(Severity.HIGH.isCriticalOrHigher());
        }

        @Test
        @DisplayName("MEDIUM should not be critical or higher")
        void isCriticalOrHigher_quandoMedium_retornaFalse() {
            assertFalse(Severity.MEDIUM.isCriticalOrHigher());
        }

        @Test
        @DisplayName("LOW should not be critical or higher")
        void isCriticalOrHigher_quandoLow_retornaFalse() {
            assertFalse(Severity.LOW.isCriticalOrHigher());
        }

        @Test
        @DisplayName("INFO should not be critical or higher")
        void isCriticalOrHigher_quandoInfo_retornaFalse() {
            assertFalse(Severity.INFO.isCriticalOrHigher());
        }
    }

    @Nested
    @DisplayName("IsHighOrHigher")
    class IsHighOrHigher {

        @Test
        @DisplayName("CRITICAL should be high or higher")
        void isHighOrHigher_quandoCritical_retornaTrue() {
            assertTrue(Severity.CRITICAL.isHighOrHigher());
        }

        @Test
        @DisplayName("HIGH should be high or higher")
        void isHighOrHigher_quandoHigh_retornaTrue() {
            assertTrue(Severity.HIGH.isHighOrHigher());
        }

        @Test
        @DisplayName("MEDIUM should not be high or higher")
        void isHighOrHigher_quandoMedium_retornaFalse() {
            assertFalse(Severity.MEDIUM.isHighOrHigher());
        }

        @Test
        @DisplayName("LOW should not be high or higher")
        void isHighOrHigher_quandoLow_retornaFalse() {
            assertFalse(Severity.LOW.isHighOrHigher());
        }

        @Test
        @DisplayName("INFO should not be high or higher")
        void isHighOrHigher_quandoInfo_retornaFalse() {
            assertFalse(Severity.INFO.isHighOrHigher());
        }
    }
}

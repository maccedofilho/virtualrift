package com.virtualrift.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
            // TODO: Implement test
            fail("Not implemented yet");
        }

        @Test
        @DisplayName("should have correct order from critical to info")
        void ordering_quandoComparado_retornaOrdemCorreta() {
            // TODO: Implement test
            fail("Not implemented yet");
        }
    }

    @Nested
    @DisplayName("FromString")
    class FromString {

        @ParameterizedTest
        @ValueSource(strings = {"CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"})
        @DisplayName("should parse from string (case-insensitive)")
        void fromString_quandoValido_retornaSeverity(String severity) {
            // TODO: Implement test
            fail("Not implemented yet");
        }

        @Test
        @DisplayName("should throw when string is invalid")
        void fromString_quandoInvalido_lancaExcecao() {
            // TODO: Implement test
            fail("Not implemented yet");
        }
    }

    @Nested
    @DisplayName("Score")
    class Score {

        @Test
        @DisplayName("CRITICAL should have score 100")
        void score_critical_retornaCem() {
            // TODO: Implement test
            fail("Not implemented yet");
        }

        @Test
        @DisplayName("HIGH should have score 75")
        void score_high_retornaSetentaCinco() {
            // TODO: Implement test
            fail("Not implemented yet");
        }

        @Test
        @DisplayName("MEDIUM should have score 50")
        void score_medium_retornaCinquenta() {
            // TODO: Implement test
            fail("Not implemented yet");
        }

        @Test
        @DisplayName("LOW should have score 25")
        void score_low_retornaVinteCinco() {
            // TODO: Implement test
            fail("Not implemented yet");
        }

        @Test
        @DisplayName("INFO should have score 0")
        void score_info_retornaZero() {
            // TODO: Implement test
            fail("Not implemented yet");
        }
    }
}

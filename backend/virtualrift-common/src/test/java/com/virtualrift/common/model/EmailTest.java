package com.virtualrift.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Email Tests")
class EmailTest {

    @Nested
    @DisplayName("Creation")
    class Creation {

        @ParameterizedTest
        @ValueSource(strings = {
            "user@example.com",
            "first.last@example.com",
            "user+tag@example.com",
            "user@sub.example.com"
        })
        @DisplayName("should create Email from valid address")
        void from_quandoValido_retornaEmail(String validEmail) {
            // TODO: Implement test
            fail("Not implemented yet");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "invalid",
            "@example.com",
            "user@",
            "user example.com",
            "",
            " "
        })
        @DisplayName("should throw when email is invalid")
        void from_quandoInvalido_lancaExcecao(String invalidEmail) {
            // TODO: Implement test
            fail("Not implemented yet");
        }

        @Test
        @DisplayName("should throw when email is null")
        void from_quandoNulo_lancaExcecao() {
            // TODO: Implement test
            fail("Not implemented yet");
        }

        @Test
        @DisplayName("should normalize email to lowercase")
        void from_quandoMaiusculas_normalizaParaMinusculas() {
            // TODO: Implement test
            fail("Not implemented yet");
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when addresses match (case-insensitive)")
        void equals_quandoMesmoEndereco_retornaTrue() {
            // TODO: Implement test
            fail("Not implemented yet");
        }

        @Test
        @DisplayName("should not be equal when addresses differ")
        void equals_quandoEnderecoDiferente_retornaFalse() {
            // TODO: Implement test
            fail("Not implemented yet");
        }
    }

    @Nested
    @DisplayName("Domain extraction")
    class DomainExtraction {

        @Test
        @DisplayName("should extract domain from email")
        void domain_quandoChamado_retornaDominio() {
            // TODO: Implement test
            fail("Not implemented yet");
        }

        @Test
        @DisplayName("should extract local part from email")
        void localPart_quandoChamado_retornaParteLocal() {
            // TODO: Implement test
            fail("Not implemented yet");
        }
    }
}

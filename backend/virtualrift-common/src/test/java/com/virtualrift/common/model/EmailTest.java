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
            Email email = Email.of(validEmail);

            assertNotNull(email);
            assertEquals(validEmail.toLowerCase(), email.value());
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
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> Email.of(invalidEmail)
            );

            assertTrue(exception.getMessage().contains("Invalid email"));
        }

        @Test
        @DisplayName("should throw when email is null")
        void from_quandoNulo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> Email.of(null)
            );

            assertEquals("email cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("should normalize email to lowercase")
        void from_quandoMaiusculas_normalizaParaMinusculas() {
            Email email = Email.of("USER@Example.COM");

            assertEquals("user@example.com", email.value());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when addresses match (case-insensitive)")
        void equals_quandoMesmoEndereco_retornaTrue() {
            Email email1 = Email.of("user@example.com");
            Email email2 = Email.of("USER@EXAMPLE.COM");

            assertEquals(email1, email2);
            assertEquals(email1.hashCode(), email2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when addresses differ")
        void equals_quandoEnderecoDiferente_retornaFalse() {
            Email email1 = Email.of("user1@example.com");
            Email email2 = Email.of("user2@example.com");

            assertNotEquals(email1, email2);
        }
    }

    @Nested
    @DisplayName("Domain extraction")
    class DomainExtraction {

        @Test
        @DisplayName("should extract domain from email")
        void domain_quandoChamado_retornaDominio() {
            Email email = Email.of("user@example.com");

            assertEquals("example.com", email.domain());
        }

        @Test
        @DisplayName("should extract local part from email")
        void localPart_quandoChamado_retornaParteLocal() {
            Email email = Email.of("user@example.com");

            assertEquals("user", email.localPart());
        }

        @Test
        @DisplayName("should extract local part with plus tag")
        void localPart_quandoComTag_retornaParteLocalCompleta() {
            Email email = Email.of("user+tag@example.com");

            assertEquals("user+tag", email.localPart());
        }
    }
}

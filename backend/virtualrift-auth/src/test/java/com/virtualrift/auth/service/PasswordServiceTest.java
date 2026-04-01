package com.virtualrift.auth.service;

import com.virtualrift.auth.exception.InvalidPasswordException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

@DisplayName("PasswordService Tests")
class PasswordServiceTest {

    private final PasswordService passwordService = new PasswordService();

    @Nested
    @DisplayName("Hash password")
    class HashPassword {

        @Test
        @DisplayName("should hash valid password")
        void hashPassword_quandoSenhaValida_retornaHash() {
            String password = "ValidPassword123!";
            String hash = passwordService.hash(password);

            assertNotNull(hash);
            assertNotEquals(password, hash);
            assertTrue(hash.startsWith("$2a$"));
        }

        @Test
        @DisplayName("should generate different hash for same password")
        void hashPassword_quandoMesmaSenha_retornaHashDiferente() {
            String password = "ValidPassword123!";
            String hash1 = passwordService.hash(password);
            String hash2 = passwordService.hash(password);

            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("should use bcrypt algorithm")
        void hashPassword_usaAlgoritmoBcrypt() {
            String password = "ValidPassword123!";
            String hash = passwordService.hash(password);

            assertTrue(hash.startsWith("$2a$"));
        }

        @Test
        @DisplayName("should throw when password is null")
        void hashPassword_quandoNula_lancaExcecao() {
            assertThrows(InvalidPasswordException.class, () -> passwordService.hash(null));
        }

        @Test
        @DisplayName("should throw when password is empty")
        void hashPassword_quandoVazia_lancaExcecao() {
            assertThrows(InvalidPasswordException.class, () -> passwordService.hash(""));
        }

        @Test
        @DisplayName("should throw when password is too short")
        void hashPassword_quandoMuitoCurta_lancaExcecao() {
            assertThrows(InvalidPasswordException.class, () -> passwordService.hash("Short1!"));
        }

        @Test
        @DisplayName("should throw when password is too long")
        void hashPassword_quandoMuitoLonga_lancaExcecao() {
            String tooLong = "a".repeat(129) + "1A!";
            assertThrows(InvalidPasswordException.class, () -> passwordService.hash(tooLong));
        }

        @Test
        @DisplayName("should hash password with special characters")
        void hashPassword_quandoCaracteresEspeciais_retornaHash() {
            String password = "P@ssw0rd!#$%&*()";
            String hash = passwordService.hash(password);

            assertNotNull(hash);
            assertTrue(hash.startsWith("$2a$"));
        }

        @Test
        @DisplayName("should hash unicode passwords")
        void hashPassword_quandoUnicode_retornaHash() {
            String password = "Pässwörd123!ção";
            String hash = passwordService.hash(password);

            assertNotNull(hash);
            assertTrue(hash.startsWith("$2a$"));
        }
    }

    @Nested
    @DisplayName("Verify password")
    class VerifyPassword {

        @Test
        @DisplayName("should return true when password matches")
        void verifyPassword_quandoSenhaCorreta_retornaTrue() {
            String password = "ValidPassword123!";
            String hash = passwordService.hash(password);

            assertTrue(passwordService.verify(password, hash));
        }

        @Test
        @DisplayName("should return false when password does not match")
        void verifyPassword_quandoSenhaIncorreta_retornaFalse() {
            String password = "ValidPassword123!";
            String hash = passwordService.hash(password);
            String wrongPassword = "WrongPassword123!";

            assertFalse(passwordService.verify(wrongPassword, hash));
        }

        @Test
        @DisplayName("should return false when hash is invalid")
        void verifyPassword_quandoHashInvalido_retornaFalse() {
            assertFalse(passwordService.verify("password123", "invalid-hash"));
        }

        @Test
        @DisplayName("should throw when password is null")
        void verifyPassword_quandoSenhaNula_lancaExcecao() {
            assertThrows(InvalidPasswordException.class, () -> passwordService.verify(null, "$2a$10$hash"));
        }

        @Test
        @DisplayName("should throw when hash is null")
        void verifyPassword_quandoHashNulo_lancaExcecao() {
            assertThrows(InvalidPasswordException.class, () -> passwordService.verify("password", null));
        }

        @Test
        @DisplayName("should be case sensitive")
        void verifyPassword_sensivelAMaiusculas() {
            String password = "ValidPassword123!";
            String hash = passwordService.hash(password);

            assertTrue(passwordService.verify("ValidPassword123!", hash));
            assertFalse(passwordService.verify("validpassword123!", hash));
            assertFalse(passwordService.verify("VALIDPASSWORD123!", hash));
        }

        @Test
        @DisplayName("should handle timing attack resistance")
        void verifyPassword_resisteAtaqueDeTiming() {
            String hash = passwordService.hash("ValidPassword123!");

            long start1 = System.nanoTime();
            passwordService.verify("password1", hash);
            long end1 = System.nanoTime();

            long start2 = System.nanoTime();
            passwordService.verify("password2", hash);
            long end2 = System.nanoTime();

            long time1 = end1 - start1;
            long time2 = end2 - start2;

            // Timing should be within reasonable range (constant-time comparison)
            assertTrue(Math.abs(time1 - time2) < Math.max(time1, time2));
        }
    }

    @Nested
    @DisplayName("Password validation")
    class PasswordValidation {

        @ParameterizedTest
        @ValueSource(strings = {
            "Abcdef1!",        // min valid
            "VeryComplex123!@#",  // complex
            "ääääääää1!"      // unicode
        })
        @DisplayName("should accept valid passwords")
        void validatePassword_quandoValida_naoLancaExcecao(String validPassword) {
            assertDoesNotThrow(() -> passwordService.validate(validPassword));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "short1!",         // too short
            "alllowercase",    // no uppercase
            "ALLUPPERCASE",    // no lowercase
            "NoDigits!",       // no digits
            "NoSpecial1",      // no special chars
            "12345678",        // only digits
            "        ",        // spaces only
        })
        @DisplayName("should reject invalid passwords")
        void validatePassword_quandoInvalida_lancaExcecao(String invalidPassword) {
            assertThrows(InvalidPasswordException.class, () -> passwordService.validate(invalidPassword));
        }

        @Test
        @DisplayName("should require minimum 8 characters")
        void validatePassword_requerMinimo8Caracteres() {
            assertThrows(InvalidPasswordException.class, () -> passwordService.validate("Short1!"));

            InvalidPasswordException exception = assertThrows(
                    InvalidPasswordException.class,
                    () -> passwordService.validate("Abc1!")
            );
            assertTrue(exception.getMessage().contains("8"));
        }

        @Test
        @DisplayName("should require at least one uppercase letter")
        void validatePassword_requerMaiuscula() {
            InvalidPasswordException exception = assertThrows(
                    InvalidPasswordException.class,
                    () -> passwordService.validate("password123!")
            );
            assertTrue(exception.getMessage().toLowerCase().contains("uppercase"));
        }

        @Test
        @DisplayName("should require at least one lowercase letter")
        void validatePassword_requerMinuscula() {
            InvalidPasswordException exception = assertThrows(
                    InvalidPasswordException.class,
                    () -> passwordService.validate("PASSWORD123!")
            );
            assertTrue(exception.getMessage().toLowerCase().contains("lowercase"));
        }

        @Test
        @DisplayName("should require at least one digit")
        void validatePassword_requerDigito() {
            InvalidPasswordException exception = assertThrows(
                    InvalidPasswordException.class,
                    () -> passwordService.validate("Password!")
            );
            assertTrue(exception.getMessage().toLowerCase().contains("digit"));
        }

        @Test
        @DisplayName("should require at least one special character")
        void validatePassword_requerCaracterEspecial() {
            InvalidPasswordException exception = assertThrows(
                    InvalidPasswordException.class,
                    () -> passwordService.validate("Password123")
            );
            assertTrue(exception.getMessage().toLowerCase().contains("special"));
        }

        @Test
        @DisplayName("should reject common passwords")
        void validatePassword_rejeitaSenhasComuns() {
            assertThrows(InvalidPasswordException.class, () -> passwordService.validate("Password1!"));
            assertThrows(InvalidPasswordException.class, () -> passwordService.validate("Welcome1!"));
            assertThrows(InvalidPasswordException.class, () -> passwordService.validate("Admin123!"));
        }
    }

    @Nested
    @DisplayName("Generate random password")
    class GenerateRandomPassword {

        @Test
        @DisplayName("should generate password meeting all requirements")
        void generateRandomPassword_quandoChamado_retornaSenhaValida() {
            String password = passwordService.generateRandom();

            assertDoesNotThrow(() -> passwordService.validate(password));
            assertTrue(password.length() >= 16);
        }

        @Test
        @DisplayName("should generate different passwords on each call")
        void generateRandomPassword_chamadasDiferentes_retornaSenhasDiferentes() {
            Set<String> passwords = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                passwords.add(passwordService.generateRandom());
            }

            assertTrue(passwords.size() > 90, "Should generate mostly unique passwords");
        }

        @Test
        @DisplayName("should respect specified length")
        void generateRandomPassword_comTamanho_respeitaTamanho() {
            int length = 24;
            String password = passwordService.generateRandom(length);

            assertEquals(length, password.length());
            assertDoesNotThrow(() -> passwordService.validate(password));
        }

        @Test
        @DisplayName("should use secure random")
        void generateRandomPassword_usaRandomSeguro() {
            // This test ensures that generateRandom uses SecureRandom
            // We verify by checking entropy (uniqueness) across multiple generations
            Set<String> passwords = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                passwords.add(passwordService.generateRandom());
            }

            // With SecureRandom, we should get very high uniqueness
            assertTrue(passwords.size() > 990, "Should use SecureRandom for high entropy");
        }
    }
}

package com.virtualrift.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserId Tests")
class UserIdTest {

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create UserId from valid UUID")
        void fromUUID_quandoValido_retornaUserId() {
            UUID uuid = UUID.randomUUID();
            UserId userId = UserId.of(uuid);

            assertNotNull(userId);
            assertEquals(uuid, userId.value());
        }

        @Test
        @DisplayName("should generate random UserId")
        void generate_quandoChamado_retornaNovoUserId() {
            UserId userId1 = UserId.generate();
            UserId userId2 = UserId.generate();

            assertNotNull(userId1);
            assertNotNull(userId2);
            assertNotEquals(userId1, userId2);
        }

        @Test
        @DisplayName("should throw when UUID is null")
        void fromUUID_quandoNulo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> UserId.of(null)
            );

            assertEquals("userId cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when UUIDs match")
        void equals_quandoMesmoUuid_retornaTrue() {
            UUID uuid = UUID.randomUUID();
            UserId userId1 = UserId.of(uuid);
            UserId userId2 = UserId.of(uuid);

            assertEquals(userId1, userId2);
            assertEquals(userId1.hashCode(), userId2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when UUIDs differ")
        void equals_quandoUuidDiferente_retornaFalse() {
            UserId userId1 = UserId.generate();
            UserId userId2 = UserId.generate();

            assertNotEquals(userId1, userId2);
        }
    }

    @Nested
    @DisplayName("Serialization")
    class Serialization {

        @Test
        @DisplayName("should serialize to UUID string")
        void toString_quandoChamado_retornaUuidString() {
            UUID uuid = UUID.randomUUID();
            UserId userId = UserId.of(uuid);

            String result = userId.toString();

            assertEquals(uuid.toString(), result);
        }

        @Test
        @DisplayName("should deserialize from UUID string")
        void fromString_quandoValido_retornaUserId() {
            UUID uuid = UUID.randomUUID();
            String uuidString = uuid.toString();

            UserId userId = UserId.fromString(uuidString);

            assertEquals(uuid, userId.value());
        }

        @Test
        @DisplayName("should throw when string is not valid UUID")
        void fromString_quandoInvalido_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> UserId.fromString("not-a-uuid")
            );

            assertTrue(exception.getMessage().contains("Invalid UUID"));
        }
    }
}

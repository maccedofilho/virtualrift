package com.virtualrift.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TenantId Tests")
class TenantIdTest {

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create TenantId from valid UUID")
        void fromUUID_quandoValido_retornaTenantId() {
            UUID uuid = UUID.randomUUID();
            TenantId tenantId = TenantId.of(uuid);

            assertNotNull(tenantId);
            assertEquals(uuid, tenantId.value());
        }

        @Test
        @DisplayName("should generate random TenantId")
        void generate_quandoChamado_retornaNovoTenantId() {
            TenantId tenantId1 = TenantId.generate();
            TenantId tenantId2 = TenantId.generate();

            assertNotNull(tenantId1);
            assertNotNull(tenantId2);
            assertNotEquals(tenantId1, tenantId2);
        }

        @Test
        @DisplayName("should throw when UUID is null")
        void fromUUID_quandoNulo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TenantId.of(null)
            );

            assertEquals("tenantId cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when UUIDs match")
        void equals_quandoMesmoUuid_retornaTrue() {
            UUID uuid = UUID.randomUUID();
            TenantId tenantId1 = TenantId.of(uuid);
            TenantId tenantId2 = TenantId.of(uuid);

            assertEquals(tenantId1, tenantId2);
            assertEquals(tenantId1.hashCode(), tenantId2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when UUIDs differ")
        void equals_quandoUuidDiferente_retornaFalse() {
            TenantId tenantId1 = TenantId.generate();
            TenantId tenantId2 = TenantId.generate();

            assertNotEquals(tenantId1, tenantId2);
        }
    }

    @Nested
    @DisplayName("Serialization")
    class Serialization {

        @Test
        @DisplayName("should serialize to UUID string")
        void toString_quandoChamado_retornaUuidString() {
            UUID uuid = UUID.randomUUID();
            TenantId tenantId = TenantId.of(uuid);

            String result = tenantId.toString();

            assertEquals(uuid.toString(), result);
        }

        @Test
        @DisplayName("should deserialize from UUID string")
        void fromString_quandoValido_retornaTenantId() {
            UUID uuid = UUID.randomUUID();
            String uuidString = uuid.toString();

            TenantId tenantId = TenantId.fromString(uuidString);

            assertEquals(uuid, tenantId.value());
        }

        @Test
        @DisplayName("should throw when string is not valid UUID")
        void fromString_quandoInvalido_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TenantId.fromString("not-a-uuid")
            );

            assertTrue(exception.getMessage().contains("Invalid UUID"));
        }
    }
}

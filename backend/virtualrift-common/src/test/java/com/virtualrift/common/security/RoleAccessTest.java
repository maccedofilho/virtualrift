package com.virtualrift.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RoleAccess Tests")
class RoleAccessTest {

    @Test
    @DisplayName("should parse known roles from header")
    void parseRoles_quandoCabecalhoValido_retornaRolesConhecidas() {
        assertEquals(
                EnumSet.of(UserRole.OWNER, UserRole.ANALYST),
                RoleAccess.parseRoles("OWNER, analyst,unknown")
        );
    }

    @Test
    @DisplayName("should allow when at least one accepted role is present")
    void requireAny_quandoRoleAceita_preservaFluxo() {
        assertDoesNotThrow(() -> assertTrue(RoleAccess.hasAny("READER,ANALYST", UserRole.OWNER, UserRole.ANALYST)));
    }

    @Test
    @DisplayName("should reject when user has no accepted role")
    void requireAny_quandoRoleNaoAceita_lancaForbidden() {
        assertFalse(RoleAccess.hasAny("READER", UserRole.OWNER, UserRole.ANALYST));
    }
}

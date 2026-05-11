package com.virtualrift.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualrift.auth.config.OAuthConfig;
import com.virtualrift.auth.exception.OAuthCallbackException;
import com.virtualrift.auth.exception.OAuthRedirectUriException;
import com.virtualrift.auth.model.OAuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("OAuthStateService Tests")
class OAuthStateServiceTest {

    private OAuthStateService createService() {
        OAuthConfig config = new OAuthConfig();
        config.setStateSecret("test-state-secret");
        config.setAllowedRedirectOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        config.setStateTtlSeconds(300);
        return new OAuthStateService(config, new ObjectMapper());
    }

    @Test
    @DisplayName("should create and parse signed state for an allowed redirect URI")
    void createState_quandoRedirectPermitido_retornaStateValido() {
        OAuthStateService service = createService();

        String state = service.createState(OAuthProvider.GITHUB, "http://localhost:5173/#/auth/callback?provider=github");
        OAuthStateService.OAuthStatePayload payload = service.parseState(state);

        assertEquals("github", payload.provider());
        assertEquals("http://localhost:5173/#/auth/callback?provider=github", payload.redirectUri());
    }

    @Test
    @DisplayName("should reject redirect URIs outside the allowlist")
    void validateRedirectUri_quandoOrigemNaoPermitida_rejeita() {
        OAuthStateService service = createService();

        assertThrows(
                OAuthRedirectUriException.class,
                () -> service.validateRedirectUri("https://evil.example.com/#/auth/callback")
        );
    }

    @Test
    @DisplayName("should reject tampered OAuth state payloads")
    void parseState_quandoAssinaturaFoiAlterada_rejeita() {
        OAuthStateService service = createService();
        String state = service.createState(OAuthProvider.GITHUB, "http://localhost:5173/#/auth/callback?provider=github");
        String tampered = state + "tampered";

        assertThrows(OAuthCallbackException.class, () -> service.parseState(tampered));
    }
}

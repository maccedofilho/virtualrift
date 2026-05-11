package com.virtualrift.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualrift.auth.config.OAuthConfig;
import com.virtualrift.auth.exception.OAuthUserProvisioningException;
import com.virtualrift.auth.model.OAuthProvider;
import com.virtualrift.auth.model.RefreshToken;
import com.virtualrift.auth.model.Token;
import com.virtualrift.auth.model.User;
import com.virtualrift.auth.model.UserIdentity;
import com.virtualrift.auth.model.UserStatus;
import com.virtualrift.auth.repository.UserIdentityRepository;
import com.virtualrift.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthLoginService Tests")
class OAuthLoginServiceTest {

    @Mock
    private GitHubOAuthClient gitHubOAuthClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordService passwordService;

    private OAuthConfig config;
    private OAuthStateService stateService;
    private OAuthLoginService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        config = new OAuthConfig();
        config.setPublicBaseUrl("http://localhost:8080");
        config.setStateSecret("oauth-state-secret");
        config.setAllowedRedirectOrigins(List.of("http://localhost:5173"));
        config.setStateTtlSeconds(300);
        config.getGithub().setEnabled(true);
        config.getGithub().setClientId("github-client-id");
        config.getGithub().setClientSecret("github-client-secret");

        stateService = new OAuthStateService(config, new ObjectMapper());
        service = new OAuthLoginService(
                config,
                stateService,
                gitHubOAuthClient,
                userRepository,
                userIdentityRepository,
                jwtService,
                refreshTokenService,
                passwordService
        );
    }

    @Test
    @DisplayName("should build GitHub authorize redirect with signed state")
    void buildGitHubStartRedirect_quandoConfigurado_retornaAuthorizeUri() {
        URI redirect = service.buildGitHubStartRedirect("http://localhost:5173/#/auth/callback?provider=github");

        assertEquals("github.com", redirect.getHost());
        assertTrue(redirect.toString().contains("client_id=github-client-id"));
        assertTrue(redirect.toString().contains("redirect_uri="));
        assertTrue(redirect.toString().contains("/api/v1/auth/oauth/github/callback"));
        assertTrue(redirect.toString().contains("state="));
    }

    @Test
    @DisplayName("should redirect back with provider_not_configured when GitHub is disabled")
    void buildGitHubStartRedirect_quandoProviderDesligado_retornaErroNoFrontend() {
        config.getGithub().setEnabled(false);

        URI redirect = service.buildGitHubStartRedirect("http://localhost:5173/#/auth/callback?provider=github");

        assertTrue(redirect.toString().contains("error=provider_not_configured"));
        assertTrue(redirect.toString().contains("provider=github"));
    }

    @Test
    @DisplayName("should link an existing user by email and redirect back with VirtualRift tokens")
    void handleGitHubCallback_quandoEmailJaExiste_vinculaIdentityERedirecionaComTokens() {
        String frontendRedirect = "http://localhost:5173/#/auth/callback?provider=github";
        String state = stateService.createState(OAuthProvider.GITHUB, frontendRedirect);
        User user = new User(userId, "owner@virtualrift.test", "hash", tenantId, UserStatus.ACTIVE, Set.of("OWNER"));

        when(gitHubOAuthClient.exchangeCodeForIdentity("github-code", "http://localhost:8080/api/v1/auth/oauth/github/callback"))
                .thenReturn(new GitHubOAuthClient.GitHubIdentity("github-subject-1", "owner@virtualrift.test", "Owner User", "owner-user"));
        when(userIdentityRepository.findByProviderAndProviderSubject(OAuthProvider.GITHUB, "github-subject-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("owner@virtualrift.test")).thenReturn(Optional.of(user));
        when(userIdentityRepository.existsByUserIdAndProvider(userId, OAuthProvider.GITHUB)).thenReturn(false);
        when(jwtService.generate(userId, tenantId, user.roles())).thenReturn(new Token("access-token", "jwt-refresh", Instant.now().plusSeconds(900)));
        when(refreshTokenService.generate(userId, tenantId)).thenReturn(new RefreshToken("refresh-token", userId, tenantId, Instant.now().plusSeconds(3600)));

        URI redirect = service.handleGitHubCallback("github-code", state, null);

        assertTrue(redirect.toString().contains("accessToken=access-token"));
        assertTrue(redirect.toString().contains("refreshToken=refresh-token"));
        ArgumentCaptor<UserIdentity> identityCaptor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).save(identityCaptor.capture());
        assertEquals(userId, identityCaptor.getValue().userId());
        assertEquals(tenantId, identityCaptor.getValue().tenantId());
        assertEquals(OAuthProvider.GITHUB, identityCaptor.getValue().provider());
    }

    @Test
    @DisplayName("should auto provision a new user when enabled")
    void handleGitHubCallback_quandoAutoProvisionLigado_criaUsuarioNovo() {
        config.getAutoProvision().setEnabled(true);
        config.getAutoProvision().setTenantId(tenantId);
        config.getAutoProvision().setRoles(Set.of("ANALYST"));

        String frontendRedirect = "http://localhost:5173/#/auth/callback?provider=github";
        String state = stateState(frontendRedirect);
        User createdUser = new User(userId, "new@virtualrift.test", "hash", tenantId, UserStatus.ACTIVE, Set.of("ANALYST"));

        when(gitHubOAuthClient.exchangeCodeForIdentity("github-code", "http://localhost:8080/api/v1/auth/oauth/github/callback"))
                .thenReturn(new GitHubOAuthClient.GitHubIdentity("github-subject-2", "new@virtualrift.test", "New User", "new-user"));
        when(userIdentityRepository.findByProviderAndProviderSubject(OAuthProvider.GITHUB, "github-subject-2"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@virtualrift.test")).thenReturn(Optional.empty());
        when(passwordService.generateRandom()).thenReturn("ValidPassword123!");
        when(passwordService.hash("ValidPassword123!")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenReturn(createdUser);
        when(userIdentityRepository.existsByUserIdAndProvider(userId, OAuthProvider.GITHUB)).thenReturn(false);
        when(jwtService.generate(userId, tenantId, createdUser.roles())).thenReturn(new Token("access-token", "jwt-refresh", Instant.now().plusSeconds(900)));
        when(refreshTokenService.generate(userId, tenantId)).thenReturn(new RefreshToken("refresh-token", userId, tenantId, Instant.now().plusSeconds(3600)));

        URI redirect = service.handleGitHubCallback("github-code", state, null);

        assertTrue(redirect.toString().contains("accessToken=access-token"));
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("should reject unprovisioned social accounts when auto provisioning is disabled")
    void handleGitHubCallback_quandoSemUsuarioEAutoProvisionDesligado_rejeita() {
        String state = stateState("http://localhost:5173/#/auth/callback?provider=github");

        when(gitHubOAuthClient.exchangeCodeForIdentity("github-code", "http://localhost:8080/api/v1/auth/oauth/github/callback"))
                .thenReturn(new GitHubOAuthClient.GitHubIdentity("github-subject-3", "new@virtualrift.test", "New User", "new-user"));
        when(userIdentityRepository.findByProviderAndProviderSubject(OAuthProvider.GITHUB, "github-subject-3"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@virtualrift.test")).thenReturn(Optional.empty());

        assertThrows(OAuthUserProvisioningException.class, () -> service.handleGitHubCallback("github-code", state, null));
    }

    private String stateState(String frontendRedirect) {
        return stateService.createState(OAuthProvider.GITHUB, frontendRedirect);
    }
}

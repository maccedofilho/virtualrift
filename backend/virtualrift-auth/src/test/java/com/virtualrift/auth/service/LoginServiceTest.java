package com.virtualrift.auth.service;

import com.virtualrift.auth.dto.LoginRequest;
import com.virtualrift.auth.dto.LoginResponse;
import com.virtualrift.auth.exception.InvalidCredentialsException;
import com.virtualrift.auth.exception.UserPendingVerificationException;
import com.virtualrift.auth.exception.UserSuspendedException;
import com.virtualrift.auth.exception.UserDeletedException;
import com.virtualrift.auth.model.User;
import com.virtualrift.auth.model.UserStatus;
import com.virtualrift.auth.repository.UserRepository;
import com.virtualrift.auth.repository.LoginAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginService Tests")
class LoginServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private TokenDenylist denylist;

    private LoginService loginService;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final String email = "test@example.com";
    private final String password = "ValidPassword123!";
    private final String hashedPassword = "$2a$10$hashedPassword";

    @BeforeEach
    void setUp() {
        loginService = new LoginService(
                userRepository,
                loginAttemptRepository,
                passwordService,
                jwtService,
                refreshTokenService,
                denylist
        );
    }

    private User createValidUser() {
        return new User(userId, email, hashedPassword, tenantId, UserStatus.ACTIVE, Set.of("USER"));
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("should return tokens when credentials are valid")
        void login_quandoCredenciaisValidas_retornaTokens() {
            User user = createValidUser();
            LoginRequest request = new LoginRequest(email, password);

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(user));
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);
            when(jwtService.generate(userId, tenantId, user.roles()))
                    .thenReturn(new com.virtualrift.auth.model.Token("access-token", "refresh-token"));
            when(loginAttemptRepository.getFailedAttempts(email.toLowerCase())).thenReturn(0);

            LoginResponse response = loginService.login(request);

            assertNotNull(response);
            assertNotNull(response.accessToken());
            assertNotNull(response.refreshToken());
        }

        @Test
        @DisplayName("should return access token and refresh token")
        void login_quandoCredenciaisValidas_retornaAccessTokenERefreshToken() {
            User user = createValidUser();
            LoginRequest request = new LoginRequest(email, password);

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(user));
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);
            when(jwtService.generate(userId, tenantId, user.roles()))
                    .thenReturn(new com.virtualrift.auth.model.Token("access-token", "refresh-token"));
            when(loginAttemptRepository.getFailedAttempts(email.toLowerCase())).thenReturn(0);

            LoginResponse response = loginService.login(request);

            assertEquals("access-token", response.accessToken());
            assertEquals("refresh-token", response.refreshToken());
        }

        @Test
        @DisplayName("should throw InvalidCredentialsException when email does not exist")
        void login_quandoEmailNaoExiste_lancaInvalidCredentialsException() {
            LoginRequest request = new LoginRequest("unknown@example.com", password);

            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            assertThrows(InvalidCredentialsException.class, () -> loginService.login(request));
            verify(loginAttemptRepository).recordFailedAttempt("unknown@example.com");
        }

        @Test
        @DisplayName("should throw InvalidCredentialsException when password is incorrect")
        void login_quandoSenhaIncorreta_lancaInvalidCredentialsException() {
            User user = createValidUser();
            LoginRequest request = new LoginRequest(email, "WrongPassword123!");

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(user));
            when(passwordService.verify("WrongPassword123!", hashedPassword)).thenReturn(false);

            assertThrows(InvalidCredentialsException.class, () -> loginService.login(request));
            verify(loginAttemptRepository).recordFailedAttempt(email.toLowerCase());
        }

        @Test
        @DisplayName("should throw InvalidCredentialsException when password is null")
        void login_quandoSenhaNula_lancaInvalidCredentialsException() {
            User user = createValidUser();
            LoginRequest request = new LoginRequest(email, null);

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(user));

            assertThrows(InvalidCredentialsException.class, () -> loginService.login(request));
        }

        @Test
        @DisplayName("should throw UserPendingVerificationException when user is pending")
        void login_quandoUsuarioPendente_lancaUserPendingVerificationException() {
            User user = new User(userId, email, hashedPassword, tenantId, UserStatus.PENDING, Set.of("USER"));
            LoginRequest request = new LoginRequest(email, password);

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(user));
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);

            assertThrows(UserPendingVerificationException.class, () -> loginService.login(request));
        }

        @Test
        @DisplayName("should throw exception when user is suspended")
        void login_quandoUsuarioSuspenso_lancaExcecao() {
            User user = new User(userId, email, hashedPassword, tenantId, UserStatus.SUSPENDED, Set.of("USER"));
            LoginRequest request = new LoginRequest(email, password);

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(user));
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);

            assertThrows(UserSuspendedException.class, () -> loginService.login(request));
        }

        @Test
        @DisplayName("should throw exception when user is deleted")
        void login_quandoUsuarioDeletado_lancaExcecao() {
            User user = new User(userId, email, hashedPassword, tenantId, UserStatus.DELETED, Set.of("USER"));
            LoginRequest request = new LoginRequest(email, password);

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(user));
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);

            assertThrows(UserDeletedException.class, () -> loginService.login(request));
        }

        @Test
        @DisplayName("should normalize email to lowercase")
        void login_quandoEmailMaiusculas_normalizaParaMinusculas() {
            User user = createValidUser();
            LoginRequest request = new LoginRequest("TEST@EXAMPLE.COM", password);

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);
            when(jwtService.generate(userId, tenantId, user.roles()))
                    .thenReturn(new com.virtualrift.auth.model.Token("access-token", "refresh-token"));
            when(loginAttemptRepository.getFailedAttempts("test@example.com")).thenReturn(0);

            loginService.login(request);

            verify(userRepository).findByEmail("test@example.com");
        }

        @Test
        @DisplayName("should trim whitespace from email")
        void login_quandoEmailComEspacos_removeEspacos() {
            User user = createValidUser();
            LoginRequest request = new LoginRequest("  test@example.com  ", password);

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);
            when(jwtService.generate(userId, tenantId, user.roles()))
                    .thenReturn(new com.virtualrift.auth.model.Token("access-token", "refresh-token"));
            when(loginAttemptRepository.getFailedAttempts("test@example.com")).thenReturn(0);

            loginService.login(request);

            verify(userRepository).findByEmail("test@example.com");
        }

        @Test
        @DisplayName("should include tenantId in token")
        void login_quandoSucesso_tokenContemTenantId() {
            User user = createValidUser();
            LoginRequest request = new LoginRequest(email, password);

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(user));
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);
            when(jwtService.generate(userId, tenantId, user.roles()))
                    .thenReturn(new com.virtualrift.auth.model.Token("access-token", "refresh-token"));
            when(loginAttemptRepository.getFailedAttempts(email.toLowerCase())).thenReturn(0);

            loginService.login(request);

            verify(jwtService).generate(userId, tenantId, user.roles());
        }

        @Test
        @DisplayName("should include roles in token")
        void login_quandoSucesso_tokenContemRoles() {
            User user = createValidUser();
            Set<String> roles = Set.of("USER", "ADMIN");
            User userWithRoles = new User(userId, email, hashedPassword, tenantId, UserStatus.ACTIVE, roles);
            LoginRequest request = new LoginRequest(email, password);

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(userWithRoles));
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);
            when(jwtService.generate(userId, tenantId, roles))
                    .thenReturn(new com.virtualrift.auth.model.Token("access-token", "refresh-token"));
            when(loginAttemptRepository.getFailedAttempts(email.toLowerCase())).thenReturn(0);

            loginService.login(request);

            verify(jwtService).generate(userId, tenantId, roles);
        }

        @Test
        @DisplayName("should record login attempt")
        void login_quandoSucesso_registraTentativa() {
            User user = createValidUser();
            LoginRequest request = new LoginRequest(email, password);

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(user));
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);
            when(jwtService.generate(userId, tenantId, user.roles()))
                    .thenReturn(new com.virtualrift.auth.model.Token("access-token", "refresh-token"));
            when(loginAttemptRepository.getFailedAttempts(email.toLowerCase())).thenReturn(0);

            loginService.login(request);

            verify(loginAttemptRepository).clearFailedAttempts(email.toLowerCase());
        }

        @Test
        @DisplayName("should record failed login attempt")
        void login_quandoFalha_registraTentativaFalha() {
            LoginRequest request = new LoginRequest(email, "WrongPassword123!");

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(createValidUser()));
            when(passwordService.verify("WrongPassword123!", hashedPassword)).thenReturn(false);

            assertThrows(InvalidCredentialsException.class, () -> loginService.login(request));

            verify(loginAttemptRepository).recordFailedAttempt(email.toLowerCase());
        }

        @Test
        @DisplayName("should lock account after too many failed attempts")
        void login_quandoMuitasTentativasFalhas_bloqueiaConta() {
            User user = createValidUser();
            LoginRequest request = new LoginRequest(email, password);

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(user));
            when(loginAttemptRepository.getFailedAttempts(email.toLowerCase())).thenReturn(6);

            assertThrows(InvalidCredentialsException.class, () -> loginService.login(request));

            verify(loginAttemptRepository, never()).clearFailedAttempts(anyString());
        }
    }

    @Nested
    @DisplayName("Logout")
    class Logout {

        @Test
        @DisplayName("should revoke access token")
        void logout_quandoChamado_revogaAccessToken() {
            String accessToken = "access-token";
            String refreshToken = "refresh-token";

            doNothing().when(denylist).add(eq(accessToken), any());

            assertDoesNotThrow(() -> loginService.logout(accessToken, refreshToken));

            verify(denylist).add(eq(accessToken), any());
        }

        @Test
        @DisplayName("should revoke refresh token")
        void logout_quandoChamado_revogaRefreshToken() {
            String accessToken = "access-token";
            String refreshToken = "refresh-token";

            lenient().doNothing().when(denylist).add(eq(accessToken), any());
            when(refreshTokenService.revoke(refreshToken)).thenReturn(userId);

            loginService.logout(accessToken, refreshToken);

            verify(refreshTokenService).revoke(refreshToken);
        }

        @Test
        @DisplayName("should add tokens to denylist")
        void logout_quandoChamado_adicionaNaDenylist() {
            String accessToken = "access-token";
            String refreshToken = "refresh-token";

            loginService.logout(accessToken, refreshToken);

            verify(denylist).add(eq(accessToken), any());
        }

        @Test
        @DisplayName("should handle token not in denylist gracefully")
        void logout_quandoTokenNaoEncontrado_naoLancaExcecao() {
            String accessToken = "access-token";
            String refreshToken = "refresh-token";

            lenient().doThrow(new RuntimeException("not found")).when(refreshTokenService).revoke(refreshToken);

            assertDoesNotThrow(() -> loginService.logout(accessToken, refreshToken));
        }

        @Test
        @DisplayName("should be idempotent")
        void logout_quandoJaRevogado_naoLancaExcecao() {
            String accessToken = "access-token";
            String refreshToken = "refresh-token";

            assertDoesNotThrow(() -> {
                loginService.logout(accessToken, refreshToken);
                loginService.logout(accessToken, refreshToken);
            });
        }
    }

    @Nested
    @DisplayName("Refresh token")
    class RefreshToken {

        @Test
        @DisplayName("should return new access token when refresh token is valid")
        void refreshToken_quandoValido_retornaNovoAccessToken() {
            String refreshTokenValue = "valid-refresh-token";
            RefreshToken refreshToken = new RefreshToken(refreshTokenValue, userId, tenantId, java.time.Instant.now().plusDays(7));

            when(refreshTokenService.validate(refreshTokenValue)).thenReturn(userId);
            when(refreshTokenService.rotate(refreshTokenValue)).thenReturn(refreshToken);
            when(jwtService.generate(userId, tenantId, Set.of("USER")))
                    .thenReturn(new com.virtualrift.auth.model.Token("new-access-token", "new-refresh-token"));

            LoginResponse response = loginService.refreshToken(refreshTokenValue);

            assertNotNull(response);
            assertEquals("new-access-token", response.accessToken());
        }

        @Test
        @DisplayName("should keep same userId in new token")
        void refreshToken_quandoValido_mesmoUserId() {
            String refreshTokenValue = "valid-refresh-token";
            RefreshToken refreshToken = new RefreshToken(refreshTokenValue, userId, tenantId, java.time.Instant.now().plusDays(7));

            when(refreshTokenService.validate(refreshTokenValue)).thenReturn(userId);
            when(refreshTokenService.rotate(refreshTokenValue)).thenReturn(refreshToken);
            when(jwtService.generate(eq(userId), any(), any()))
                    .thenReturn(new com.virtualrift.auth.model.Token("new-access-token", "new-refresh-token"));

            loginService.refreshToken(refreshTokenValue);

            verify(jwtService).generate(eq(userId), any(), any());
        }

        @Test
        @DisplayName("should keep same tenantId in new token")
        void refreshToken_quandoValido_mesmoTenantId() {
            String refreshTokenValue = "valid-refresh-token";
            RefreshToken refreshToken = new RefreshToken(refreshTokenValue, userId, tenantId, java.time.Instant.now().plusDays(7));

            when(refreshTokenService.validate(refreshTokenValue)).thenReturn(userId);
            when(refreshTokenService.rotate(refreshTokenValue)).thenReturn(refreshToken);
            when(jwtService.generate(any(), eq(tenantId), any()))
                    .thenReturn(new com.virtualrift.auth.model.Token("new-access-token", "new-refresh-token"));

            loginService.refreshToken(refreshTokenValue);

            verify(jwtService).generate(any(), eq(tenantId), any());
        }

        @Test
        @DisplayName("should throw when refresh token is expired")
        void refreshToken_quandoExpirado_lancaExcecao() {
            String expiredToken = "expired-refresh-token";

            when(refreshTokenService.validate(expiredToken))
                    .thenThrow(new com.virtualrift.auth.exception.ExpiredTokenException("Token expired"));

            assertThrows(com.virtualrift.auth.exception.ExpiredTokenException.class,
                    () -> loginService.refreshToken(expiredToken));
        }

        @Test
        @DisplayName("should throw when refresh token is revoked")
        void refreshToken_quandoRevogado_lancaExcecao() {
            String revokedToken = "revoked-refresh-token";

            when(refreshTokenService.validate(revokedToken))
                    .thenThrow(new com.virtualrift.auth.exception.InvalidTokenException("Token revoked"));

            assertThrows(com.virtualrift.auth.exception.InvalidTokenException.class,
                    () -> loginService.refreshToken(revokedToken));
        }

        @Test
        @DisplayName("should rotate refresh token")
        void refreshToken_quandoValido_geraNovoRefreshToken() {
            String oldRefreshToken = "old-refresh-token";
            RefreshToken newRefreshToken = new RefreshToken("new-refresh-token", userId, tenantId, java.time.Instant.now().plusDays(7));

            when(refreshTokenService.validate(oldRefreshToken)).thenReturn(userId);
            when(refreshTokenService.rotate(oldRefreshToken)).thenReturn(newRefreshToken);
            when(jwtService.generate(userId, tenantId, Set.of("USER")))
                    .thenReturn(new com.virtualrift.auth.model.Token("new-access-token", "new-refresh-token"));

            LoginResponse response = loginService.refreshToken(oldRefreshToken);

            assertEquals("new-refresh-token", response.refreshToken());
            verify(refreshTokenService).rotate(oldRefreshToken);
        }
    }
}

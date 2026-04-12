package com.virtualrift.auth.service;

import com.virtualrift.auth.dto.LoginRequest;
import com.virtualrift.auth.dto.LoginResponse;
import com.virtualrift.auth.exception.InvalidTokenException;
import com.virtualrift.auth.exception.InvalidCredentialsException;
import com.virtualrift.auth.exception.UserDeletedException;
import com.virtualrift.auth.exception.UserPendingVerificationException;
import com.virtualrift.auth.exception.UserSuspendedException;
import com.virtualrift.auth.model.RefreshToken;
import com.virtualrift.auth.model.Token;
import com.virtualrift.auth.model.User;
import com.virtualrift.auth.model.UserStatus;
import com.virtualrift.auth.repository.LoginAttemptRepository;
import com.virtualrift.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private Token createToken(String accessToken, String refreshToken) {
        return new Token(accessToken, refreshToken, Instant.now().plusSeconds(900));
    }

    private RefreshToken createRefreshToken(String tokenValue) {
        return new RefreshToken(tokenValue, userId, tenantId, Instant.now().plusSeconds(7 * 24 * 3600));
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
            when(loginAttemptRepository.getFailedAttempts(email.toLowerCase())).thenReturn(0);
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);
            when(jwtService.generate(userId, tenantId, user.roles())).thenReturn(createToken("access-token", "jwt-refresh"));
            when(refreshTokenService.generate(userId, tenantId)).thenReturn(createRefreshToken("refresh-token"));

            LoginResponse response = loginService.login(request);

            assertNotNull(response);
            assertEquals("access-token", response.accessToken());
            assertEquals("refresh-token", response.refreshToken());
        }

        @Test
        @DisplayName("should normalize email and record successful login")
        void login_quandoCredenciaisValidas_normalizaEmailERegistraSucesso() {
            User user = createValidUser();
            LoginRequest request = new LoginRequest("  TEST@EXAMPLE.COM  ", password);

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(loginAttemptRepository.getFailedAttempts("test@example.com")).thenReturn(0);
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);
            when(jwtService.generate(userId, tenantId, user.roles())).thenReturn(createToken("access-token", "jwt-refresh"));
            when(refreshTokenService.generate(userId, tenantId)).thenReturn(createRefreshToken("refresh-token"));

            loginService.login(request);

            verify(userRepository).findByEmail("test@example.com");
            verify(loginAttemptRepository).clearFailedAttempts("test@example.com");
            verify(loginAttemptRepository).recordSuccessfulAttempt("test@example.com");
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
        @DisplayName("should throw UserPendingVerificationException when user is pending")
        void login_quandoUsuarioPendente_lancaUserPendingVerificationException() {
            User user = new User(userId, email, hashedPassword, tenantId, UserStatus.PENDING, Set.of("USER"));
            LoginRequest request = new LoginRequest(email, password);

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(user));
            when(loginAttemptRepository.getFailedAttempts(email.toLowerCase())).thenReturn(0);
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);

            assertThrows(UserPendingVerificationException.class, () -> loginService.login(request));
            verify(jwtService, never()).generate(any(), any(), any());
        }

        @Test
        @DisplayName("should throw exception when user is suspended")
        void login_quandoUsuarioSuspenso_lancaExcecao() {
            User user = new User(userId, email, hashedPassword, tenantId, UserStatus.SUSPENDED, Set.of("USER"));
            LoginRequest request = new LoginRequest(email, password);

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(user));
            when(loginAttemptRepository.getFailedAttempts(email.toLowerCase())).thenReturn(0);
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);

            assertThrows(UserSuspendedException.class, () -> loginService.login(request));
            verify(jwtService, never()).generate(any(), any(), any());
        }

        @Test
        @DisplayName("should throw exception when user is deleted")
        void login_quandoUsuarioDeletado_lancaExcecao() {
            User user = new User(userId, email, hashedPassword, tenantId, UserStatus.DELETED, Set.of("USER"));
            LoginRequest request = new LoginRequest(email, password);

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(user));
            when(loginAttemptRepository.getFailedAttempts(email.toLowerCase())).thenReturn(0);
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);

            assertThrows(UserDeletedException.class, () -> loginService.login(request));
            verify(jwtService, never()).generate(any(), any(), any());
        }

        @Test
        @DisplayName("should include tenantId in token")
        void login_quandoSucesso_tokenContemTenantId() {
            User user = createValidUser();
            LoginRequest request = new LoginRequest(email, password);

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(user));
            when(loginAttemptRepository.getFailedAttempts(email.toLowerCase())).thenReturn(0);
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);
            when(jwtService.generate(userId, tenantId, user.roles())).thenReturn(createToken("access-token", "jwt-refresh"));
            when(refreshTokenService.generate(userId, tenantId)).thenReturn(createRefreshToken("refresh-token"));

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
            when(loginAttemptRepository.getFailedAttempts(email.toLowerCase())).thenReturn(0);
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);
            when(jwtService.generate(userId, tenantId, roles)).thenReturn(createToken("access-token", "jwt-refresh"));
            when(refreshTokenService.generate(userId, tenantId)).thenReturn(createRefreshToken("refresh-token"));

            loginService.login(request);

            verify(jwtService).generate(userId, tenantId, roles);
        }

        @Test
        @DisplayName("should record login attempt")
        void login_quandoSucesso_registraTentativa() {
            User user = createValidUser();
            LoginRequest request = new LoginRequest(email, password);

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(user));
            when(loginAttemptRepository.getFailedAttempts(email.toLowerCase())).thenReturn(0);
            when(passwordService.verify(password, hashedPassword)).thenReturn(true);
            when(jwtService.generate(userId, tenantId, user.roles())).thenReturn(createToken("access-token", "jwt-refresh"));
            when(refreshTokenService.generate(userId, tenantId)).thenReturn(createRefreshToken("refresh-token"));

            loginService.login(request);

            verify(loginAttemptRepository).clearFailedAttempts(email.toLowerCase());
            verify(loginAttemptRepository).recordSuccessfulAttempt(email.toLowerCase());
        }

        @Test
        @DisplayName("should record failed login attempt")
        void login_quandoFalha_registraTentativaFalha() {
            LoginRequest request = new LoginRequest(email, "WrongPassword123!");

            when(userRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(createValidUser()));
            when(loginAttemptRepository.getFailedAttempts(email.toLowerCase())).thenReturn(0);
            when(passwordService.verify("WrongPassword123!", hashedPassword)).thenReturn(false);

            assertThrows(InvalidCredentialsException.class, () -> loginService.login(request));

            verify(loginAttemptRepository).recordFailedAttempt(email.toLowerCase());
        }

        @Test
        @DisplayName("should lock account after too many failed attempts")
        void login_quandoMuitasTentativasFalhas_bloqueiaConta() {
            LoginRequest request = new LoginRequest(email, password);

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

            assertDoesNotThrow(() -> loginService.logout(accessToken, refreshToken));

            verify(denylist).add(eq(accessToken), any());
        }

        @Test
        @DisplayName("should revoke refresh token")
        void logout_quandoChamado_revogaRefreshToken() {
            String accessToken = "access-token";
            String refreshToken = "refresh-token";

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
        @DisplayName("should ignore invalid refresh token during logout")
        void logout_quandoRefreshTokenInvalido_naoLancaExcecao() {
            String accessToken = "access-token";
            String refreshToken = "refresh-token";

            doThrow(new InvalidTokenException("invalid")).when(refreshTokenService).revoke(refreshToken);

            assertDoesNotThrow(() -> loginService.logout(accessToken, refreshToken));
        }

        @Test
        @DisplayName("should not rollback logout when refresh token is invalid")
        void logout_quandoRefreshTokenInvalido_naoMarcaRollback() throws Exception {
            Method method = LoginService.class.getDeclaredMethod("logout", String.class, String.class);
            Transactional transactional = method.getAnnotation(Transactional.class);

            assertArrayEquals(new Class<?>[]{InvalidTokenException.class}, transactional.noRollbackFor());
        }

        @Test
        @DisplayName("should ignore blank access token")
        void logout_quandoAccessTokenVazio_naoAdicionaNaDenylist() {
            loginService.logout(" ", "refresh-token");

            verify(denylist, never()).add(anyString(), any());
        }
    }

    @Nested
    @DisplayName("Refresh token")
    class RefreshTokenFlow {

        @Test
        @DisplayName("should return new access token when refresh token is valid")
        void refreshToken_quandoValido_retornaNovoAccessToken() {
            String refreshTokenValue = "valid-refresh-token";
            User user = createValidUser();
            RefreshToken refreshToken = createRefreshToken("new-refresh-token");

            when(refreshTokenService.validate(refreshTokenValue)).thenReturn(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(refreshTokenService.rotate(refreshTokenValue)).thenReturn(refreshToken);
            when(jwtService.generate(userId, tenantId, user.roles())).thenReturn(createToken("new-access-token", "jwt-refresh"));

            LoginResponse response = loginService.refreshToken(refreshTokenValue);

            assertNotNull(response);
            assertEquals("new-access-token", response.accessToken());
            assertEquals("new-refresh-token", response.refreshToken());
        }

        @Test
        @DisplayName("should allow writes when rotating refresh token")
        void refreshToken_quandoRotaciona_naoDeveSerReadOnly() throws Exception {
            Method method = LoginService.class.getDeclaredMethod("refreshToken", String.class);
            Transactional transactional = method.getAnnotation(Transactional.class);

            assertFalse(transactional.readOnly());
        }

        @Test
        @DisplayName("should keep same userId in new token")
        void refreshToken_quandoValido_mesmoUserId() {
            String refreshTokenValue = "valid-refresh-token";
            User user = createValidUser();
            RefreshToken refreshToken = createRefreshToken("new-refresh-token");

            when(refreshTokenService.validate(refreshTokenValue)).thenReturn(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(refreshTokenService.rotate(refreshTokenValue)).thenReturn(refreshToken);
            when(jwtService.generate(eq(userId), any(), any())).thenReturn(createToken("new-access-token", "jwt-refresh"));

            loginService.refreshToken(refreshTokenValue);

            verify(jwtService).generate(eq(userId), any(), any());
        }

        @Test
        @DisplayName("should keep same tenantId in new token")
        void refreshToken_quandoValido_mesmoTenantId() {
            String refreshTokenValue = "valid-refresh-token";
            User user = createValidUser();
            RefreshToken refreshToken = createRefreshToken("new-refresh-token");

            when(refreshTokenService.validate(refreshTokenValue)).thenReturn(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(refreshTokenService.rotate(refreshTokenValue)).thenReturn(refreshToken);
            when(jwtService.generate(any(), eq(tenantId), any())).thenReturn(createToken("new-access-token", "jwt-refresh"));

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
            User user = createValidUser();
            RefreshToken newRefreshToken = createRefreshToken("new-refresh-token");

            when(refreshTokenService.validate(oldRefreshToken)).thenReturn(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(refreshTokenService.rotate(oldRefreshToken)).thenReturn(newRefreshToken);
            when(jwtService.generate(userId, tenantId, user.roles())).thenReturn(createToken("new-access-token", "jwt-refresh"));

            LoginResponse response = loginService.refreshToken(oldRefreshToken);

            assertEquals("new-refresh-token", response.refreshToken());
            verify(refreshTokenService).rotate(oldRefreshToken);
        }

        @Test
        @DisplayName("should throw when refresh token user is not found")
        void refreshToken_quandoUsuarioNaoExiste_lancaExcecao() {
            when(refreshTokenService.validate("valid-refresh-token")).thenReturn(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThrows(InvalidTokenException.class, () -> loginService.refreshToken("valid-refresh-token"));
        }
    }
}

package com.virtualrift.auth.service;

import com.virtualrift.auth.dto.LoginRequest;
import com.virtualrift.auth.dto.LoginResponse;
import com.virtualrift.auth.exception.ExpiredTokenException;
import com.virtualrift.auth.exception.InvalidCredentialsException;
import com.virtualrift.auth.exception.InvalidTokenException;
import com.virtualrift.auth.exception.UserDeletedException;
import com.virtualrift.auth.exception.UserPendingVerificationException;
import com.virtualrift.auth.exception.UserSuspendedException;
import com.virtualrift.auth.model.RefreshToken;
import com.virtualrift.auth.model.Token;
import com.virtualrift.auth.model.User;
import com.virtualrift.auth.model.UserStatus;
import com.virtualrift.auth.repository.LoginAttemptRepository;
import com.virtualrift.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TokenDenylist denylist;

    public LoginService(
            UserRepository userRepository,
            LoginAttemptRepository loginAttemptRepository,
            PasswordService passwordService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            TokenDenylist denylist
    ) {
        this.userRepository = userRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.denylist = denylist;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();

        int failedAttempts = loginAttemptRepository.getFailedAttempts(normalizedEmail);
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            log.warn("Login attempt for locked account: {}", normalizedEmail);
            loginAttemptRepository.recordFailedAttempt(normalizedEmail);
            throw new InvalidCredentialsException("Account locked due to too many failed attempts");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    loginAttemptRepository.recordFailedAttempt(normalizedEmail);
                    return new InvalidCredentialsException("Invalid credentials");
                });

        boolean passwordValid = passwordService.verify(request.password(), user.password());
        if (!passwordValid) {
            loginAttemptRepository.recordFailedAttempt(normalizedEmail);
            log.warn("Failed login attempt for user: {}", normalizedEmail);
            throw new InvalidCredentialsException("Invalid credentials");
        }

        checkUserStatus(user);

        loginAttemptRepository.clearFailedAttempts(normalizedEmail);
        loginAttemptRepository.recordSuccessfulAttempt(normalizedEmail);

        Token token = jwtService.generate(user.id(), user.tenantId(), user.roles());
        RefreshToken refreshToken = refreshTokenService.generate(user.id(), user.tenantId());

        log.info("User logged in successfully: {}", normalizedEmail);
        return new LoginResponse(token.accessToken(), refreshToken.token());
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            denylist.add(accessToken, Instant.now());
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                refreshTokenService.revoke(refreshToken);
            } catch (InvalidTokenException e) {
                log.debug("Refresh token already revoked or invalid: {}", e.getMessage());
            }
        }

        log.info("User logged out");
    }

    @Transactional(readOnly = true)
    public LoginResponse refreshToken(String refreshTokenValue) {
        UUID userId = refreshTokenService.validate(refreshTokenValue);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        checkUserStatus(user);

        RefreshToken newRefreshToken = refreshTokenService.rotate(refreshTokenValue);

        Token token = jwtService.generate(user.id(), user.tenantId(), user.roles());

        log.info("Token refreshed for user: {}", user.email());
        return new LoginResponse(token.accessToken(), newRefreshToken.token());
    }

    private void checkUserStatus(User user) {
        switch (user.status()) {
            case PENDING -> throw new UserPendingVerificationException(
                    "User account is pending verification. Please check your email.");
            case SUSPENDED -> throw new UserSuspendedException(
                    "User account is suspended. Please contact support.");
            case DELETED -> throw new UserDeletedException(
                    "User account is deleted.");
            case ACTIVE -> {    
            }
        }
    }

    @Transactional(readOnly = true)
    public int getFailedAttempts(String email) {
        return loginAttemptRepository.getFailedAttempts(email.toLowerCase().trim());
    }
}

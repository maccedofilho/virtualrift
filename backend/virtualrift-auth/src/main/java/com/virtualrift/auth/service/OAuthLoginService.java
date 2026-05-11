package com.virtualrift.auth.service;

import com.virtualrift.auth.config.OAuthConfig;
import com.virtualrift.auth.dto.LoginResponse;
import com.virtualrift.auth.exception.OAuthCallbackException;
import com.virtualrift.auth.exception.OAuthConfigurationException;
import com.virtualrift.auth.exception.OAuthUserProvisioningException;
import com.virtualrift.auth.exception.UserDeletedException;
import com.virtualrift.auth.exception.UserPendingVerificationException;
import com.virtualrift.auth.exception.UserSuspendedException;
import com.virtualrift.auth.model.OAuthProvider;
import com.virtualrift.auth.model.RefreshToken;
import com.virtualrift.auth.model.Token;
import com.virtualrift.auth.model.User;
import com.virtualrift.auth.model.UserIdentity;
import com.virtualrift.auth.model.UserStatus;
import com.virtualrift.auth.repository.UserIdentityRepository;
import com.virtualrift.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OAuthLoginService {

    private final OAuthConfig config;
    private final OAuthStateService stateService;
    private final GitHubOAuthClient gitHubOAuthClient;
    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordService passwordService;

    public OAuthLoginService(
            OAuthConfig config,
            OAuthStateService stateService,
            GitHubOAuthClient gitHubOAuthClient,
            UserRepository userRepository,
            UserIdentityRepository userIdentityRepository,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            PasswordService passwordService
    ) {
        this.config = config;
        this.stateService = stateService;
        this.gitHubOAuthClient = gitHubOAuthClient;
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.passwordService = passwordService;
    }

    public URI buildGitHubStartRedirect(String redirectUri) {
        stateService.validateRedirectUri(redirectUri);

        if (!config.getGithub().isConfigured()) {
            return appendToRedirectUri(redirectUri, Map.of(
                    "provider", OAuthProvider.GITHUB.wireValue(),
                    "error", "provider_not_configured"
            ));
        }

        String state = stateService.createState(OAuthProvider.GITHUB, redirectUri);

        return UriComponentsBuilder.fromUriString(config.getGithub().getAuthorizeUrl())
                .queryParam("client_id", config.getGithub().getClientId())
                .queryParam("redirect_uri", buildGitHubCallbackUri())
                .queryParam("scope", config.getGithub().getScope())
                .queryParam("state", state)
                .build()
                .toUri();
    }

    @Transactional
    public URI handleGitHubCallback(String code, String state, String error) {
        OAuthStateService.OAuthStatePayload payload = stateService.parseState(state);
        if (!OAuthProvider.GITHUB.wireValue().equals(payload.provider())) {
            throw new OAuthCallbackException("OAuth state provider does not match GitHub callback");
        }

        if (error != null && !error.isBlank()) {
            return appendToRedirectUri(payload.redirectUri(), Map.of(
                    "provider", OAuthProvider.GITHUB.wireValue(),
                    "error", error
            ));
        }

        if (!config.getGithub().isConfigured()) {
            return appendToRedirectUri(payload.redirectUri(), Map.of(
                    "provider", OAuthProvider.GITHUB.wireValue(),
                    "error", "provider_not_configured"
            ));
        }

        GitHubOAuthClient.GitHubIdentity identity = gitHubOAuthClient.exchangeCodeForIdentity(code, buildGitHubCallbackUri().toString());
        User user = resolveUser(identity);
        checkUserStatus(user);

        Token token = jwtService.generate(user.id(), user.tenantId(), user.roles());
        RefreshToken refreshToken = refreshTokenService.generate(user.id(), user.tenantId());
        LoginResponse response = new LoginResponse(token.accessToken(), refreshToken.token());

        return appendToRedirectUri(payload.redirectUri(), Map.of(
                "provider", OAuthProvider.GITHUB.wireValue(),
                "accessToken", response.accessToken(),
                "refreshToken", response.refreshToken()
        ));
    }

    private URI buildGitHubCallbackUri() {
        return URI.create(trimTrailingSlash(config.getPublicBaseUrl()) + "/api/v1/auth/oauth/github/callback");
    }

    private User resolveUser(GitHubOAuthClient.GitHubIdentity identity) {
        return userIdentityRepository.findByProviderAndProviderSubject(OAuthProvider.GITHUB, identity.subject())
                .flatMap(existingIdentity -> userRepository.findById(existingIdentity.userId()))
                .orElseGet(() -> linkOrProvisionUser(identity));
    }

    private User linkOrProvisionUser(GitHubOAuthClient.GitHubIdentity identity) {
        return userRepository.findByEmail(identity.email())
                .map(existingUser -> {
                    createIdentityIfMissing(existingUser, identity);
                    return existingUser;
                })
                .orElseGet(() -> autoProvisionUser(identity));
    }

    private User autoProvisionUser(GitHubOAuthClient.GitHubIdentity identity) {
        OAuthConfig.AutoProvision autoProvision = config.getAutoProvision();
        if (!autoProvision.isEnabled() || autoProvision.getTenantId() == null) {
            throw new OAuthUserProvisioningException("OAuth account is not provisioned for any tenant");
        }

        Set<String> roles = autoProvision.getRoles();
        if (roles == null || roles.isEmpty()) {
            throw new OAuthConfigurationException("OAuth auto provisioning requires at least one role");
        }

        User user = userRepository.save(new User(
                UUID.randomUUID(),
                identity.email(),
                passwordService.hash(passwordService.generateRandom()),
                autoProvision.getTenantId(),
                UserStatus.ACTIVE,
                roles
        ));

        createIdentityIfMissing(user, identity);
        return user;
    }

    private void createIdentityIfMissing(User user, GitHubOAuthClient.GitHubIdentity identity) {
        if (userIdentityRepository.existsByUserIdAndProvider(user.id(), OAuthProvider.GITHUB)) {
            return;
        }

        userIdentityRepository.save(new UserIdentity(
                UUID.randomUUID(),
                user.id(),
                user.tenantId(),
                OAuthProvider.GITHUB,
                identity.subject(),
                identity.email(),
                identity.displayName() != null && !identity.displayName().isBlank() ? identity.displayName() : identity.username(),
                null,
                null
        ));
    }

    private URI appendToRedirectUri(String redirectUri, Map<String, String> params) {
        int hashIndex = redirectUri.indexOf('#');
        if (hashIndex == -1) {
            return URI.create(appendToQuery(redirectUri, params));
        }

        String prefix = redirectUri.substring(0, hashIndex);
        String fragment = redirectUri.substring(hashIndex + 1);
        String updatedFragment = appendToQuery(fragment, params);
        return URI.create(prefix + "#" + updatedFragment);
    }

    private String appendToQuery(String value, Map<String, String> params) {
        String separator = value.contains("?") ? "&" : "?";
        StringBuilder builder = new StringBuilder(value);
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }

            builder.append(first ? separator : "&");
            builder.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
            first = false;
        }
        return builder.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
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
}

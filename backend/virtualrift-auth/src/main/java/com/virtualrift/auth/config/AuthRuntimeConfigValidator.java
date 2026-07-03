package com.virtualrift.auth.config;

import com.virtualrift.common.runtime.RuntimeConfigGuard;
import com.virtualrift.common.runtime.RuntimeEnvironment;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthRuntimeConfigValidator {

    private final String runtimeEnvironmentValue;
    private final String datasourceUrl;
    private final String redisHost;
    private final OAuthConfig oAuthConfig;
    private final OnboardingConfig onboardingConfig;

    public AuthRuntimeConfigValidator(@Value("${virtualrift.runtime.environment:local}") String runtimeEnvironmentValue,
                                      @Value("${spring.datasource.url}") String datasourceUrl,
                                      @Value("${spring.data.redis.host}") String redisHost,
                                      OAuthConfig oAuthConfig,
                                      OnboardingConfig onboardingConfig) {
        this.runtimeEnvironmentValue = runtimeEnvironmentValue;
        this.datasourceUrl = datasourceUrl;
        this.redisHost = redisHost;
        this.oAuthConfig = oAuthConfig;
        this.onboardingConfig = onboardingConfig;
    }

    @PostConstruct
    void validate() {
        RuntimeEnvironment environment = RuntimeEnvironment.fromValue(runtimeEnvironmentValue);

        RuntimeConfigGuard.requireNonLoopbackValue("spring.datasource.url", datasourceUrl, environment);
        RuntimeConfigGuard.requireNonLoopbackValue("spring.data.redis.host", redisHost, environment);
        RuntimeConfigGuard.requirePublicUrl("auth.oauth.public-base-url", oAuthConfig.getPublicBaseUrl(), environment);
        RuntimeConfigGuard.requireNonBlank("auth.oauth.state-secret", oAuthConfig.getStateSecret(), environment);
        RuntimeConfigGuard.requireBrowserOrigins("auth.oauth.allowed-redirect-origins", oAuthConfig.getAllowedRedirectOrigins(), environment);

        if (oAuthConfig.getGithub().isEnabled()) {
            RuntimeConfigGuard.requireNonBlank("auth.oauth.github.client-id", oAuthConfig.getGithub().getClientId(), environment);
            RuntimeConfigGuard.requireNonBlank("auth.oauth.github.client-secret", oAuthConfig.getGithub().getClientSecret(), environment);
            RuntimeConfigGuard.requirePublicUrl("auth.oauth.github.authorize-url", oAuthConfig.getGithub().getAuthorizeUrl(), environment);
            RuntimeConfigGuard.requirePublicUrl("auth.oauth.github.token-url", oAuthConfig.getGithub().getTokenUrl(), environment);
            RuntimeConfigGuard.requirePublicUrl("auth.oauth.github.user-url", oAuthConfig.getGithub().getUserUrl(), environment);
            RuntimeConfigGuard.requirePublicUrl("auth.oauth.github.emails-url", oAuthConfig.getGithub().getEmailsUrl(), environment);
        }

        if (onboardingConfig.isEnabled()) {
            RuntimeConfigGuard.requireServiceUrl("auth.onboarding.tenant-service-url", onboardingConfig.getTenantServiceUrl(), environment);
            RuntimeConfigGuard.requireNonBlank("auth.onboarding.internal-api-key", onboardingConfig.getInternalApiKey(), environment);
            RuntimeConfigGuard.requireNoDefault(
                    "auth.onboarding.internal-api-key",
                    onboardingConfig.getInternalApiKey(),
                    RuntimeConfigGuard.DEFAULT_INTERNAL_API_KEY,
                    environment
            );
        }
    }
}

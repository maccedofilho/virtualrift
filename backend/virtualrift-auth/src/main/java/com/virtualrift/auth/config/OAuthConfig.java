package com.virtualrift.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@ConfigurationProperties(prefix = "auth.oauth")
public class OAuthConfig {

    private String publicBaseUrl = "http://localhost:8080";
    private String stateSecret = "";
    private long stateTtlSeconds = 300;
    private List<String> allowedRedirectOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173");
    private AutoProvision autoProvision = new AutoProvision();
    private GitHub github = new GitHub();

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getStateSecret() {
        return stateSecret;
    }

    public void setStateSecret(String stateSecret) {
        this.stateSecret = stateSecret;
    }

    public long getStateTtlSeconds() {
        return stateTtlSeconds;
    }

    public void setStateTtlSeconds(long stateTtlSeconds) {
        this.stateTtlSeconds = stateTtlSeconds;
    }

    public List<String> getAllowedRedirectOrigins() {
        return allowedRedirectOrigins;
    }

    public void setAllowedRedirectOrigins(List<String> allowedRedirectOrigins) {
        this.allowedRedirectOrigins = allowedRedirectOrigins;
    }

    public AutoProvision getAutoProvision() {
        return autoProvision;
    }

    public void setAutoProvision(AutoProvision autoProvision) {
        this.autoProvision = autoProvision;
    }

    public GitHub getGithub() {
        return github;
    }

    public void setGithub(GitHub github) {
        this.github = github;
    }

    public static class GitHub {
        private boolean enabled;
        private String clientId = "";
        private String clientSecret = "";
        private String authorizeUrl = "https://github.com/login/oauth/authorize";
        private String tokenUrl = "https://github.com/login/oauth/access_token";
        private String userUrl = "https://api.github.com/user";
        private String emailsUrl = "https://api.github.com/user/emails";
        private String scope = "read:user user:email";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getAuthorizeUrl() {
            return authorizeUrl;
        }

        public void setAuthorizeUrl(String authorizeUrl) {
            this.authorizeUrl = authorizeUrl;
        }

        public String getTokenUrl() {
            return tokenUrl;
        }

        public void setTokenUrl(String tokenUrl) {
            this.tokenUrl = tokenUrl;
        }

        public String getUserUrl() {
            return userUrl;
        }

        public void setUserUrl(String userUrl) {
            this.userUrl = userUrl;
        }

        public String getEmailsUrl() {
            return emailsUrl;
        }

        public void setEmailsUrl(String emailsUrl) {
            this.emailsUrl = emailsUrl;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public boolean isConfigured() {
            return enabled
                    && clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank();
        }
    }

    public static class AutoProvision {
        private boolean enabled;
        private UUID tenantId;
        private Set<String> roles = Set.of("READER");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public UUID getTenantId() {
            return tenantId;
        }

        public void setTenantId(UUID tenantId) {
            this.tenantId = tenantId;
        }

        public Set<String> getRoles() {
            return roles;
        }

        public void setRoles(Set<String> roles) {
            this.roles = roles;
        }
    }
}

package com.virtualrift.auth.service;

import com.virtualrift.auth.config.OAuthConfig;
import com.virtualrift.auth.exception.OAuthCallbackException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;

@Component
public class GitHubOAuthClient {

    private static final String USER_AGENT = "VirtualRift-Beta";

    private final OAuthConfig config;
    private final RestClient restClient;

    @Autowired
    public GitHubOAuthClient(OAuthConfig config) {
        this(config, RestClient.builder().build());
    }

    GitHubOAuthClient(OAuthConfig config, RestClient restClient) {
        this.config = config;
        this.restClient = restClient;
    }

    public GitHubIdentity exchangeCodeForIdentity(String code, String callbackUri) {
        if (code == null || code.isBlank()) {
            throw new OAuthCallbackException("GitHub callback did not provide an authorization code");
        }

        try {
            GitHubTokenResponse tokenResponse = exchangeCode(code, callbackUri);
            GitHubUserResponse user = fetchUser(tokenResponse.accessToken());
            String email = resolveEmail(tokenResponse.accessToken(), user);

            if (email == null || email.isBlank()) {
                throw new OAuthCallbackException("GitHub account does not expose a verified email address");
            }

            return new GitHubIdentity(String.valueOf(user.id()), email.toLowerCase(), user.name(), user.login());
        } catch (OAuthCallbackException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OAuthCallbackException("GitHub OAuth flow could not be completed");
        }
    }

    private GitHubTokenResponse exchangeCode(String code, String callbackUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", config.getGithub().getClientId());
        form.add("client_secret", config.getGithub().getClientSecret());
        form.add("code", code);
        form.add("redirect_uri", callbackUri);

        GitHubTokenResponse response = restClient.post()
                .uri(config.getGithub().getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(GitHubTokenResponse.class);

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new OAuthCallbackException("GitHub did not return an access token");
        }

        return response;
    }

    private GitHubUserResponse fetchUser(String accessToken) {
        GitHubUserResponse response = restClient.get()
                .uri(config.getGithub().getUserUrl())
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    headers.set("User-Agent", USER_AGENT);
                })
                .retrieve()
                .body(GitHubUserResponse.class);

        if (response == null || response.id() == 0) {
            throw new OAuthCallbackException("GitHub user profile is invalid");
        }

        return response;
    }

    private String resolveEmail(String accessToken, GitHubUserResponse user) {
        if (user.email() != null && !user.email().isBlank()) {
            return user.email();
        }

        List<GitHubEmailResponse> emails = restClient.get()
                .uri(config.getGithub().getEmailsUrl())
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    headers.set("User-Agent", USER_AGENT);
                })
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (emails == null || emails.isEmpty()) {
            return null;
        }

        return emails.stream()
                .filter(GitHubEmailResponse::verified)
                .sorted(Comparator.comparing(GitHubEmailResponse::primary).reversed())
                .map(GitHubEmailResponse::email)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    record GitHubTokenResponse(
            @JsonProperty("access_token") String accessToken,
            String scope,
            @JsonProperty("token_type") String tokenType
    ) {
    }

    record GitHubUserResponse(long id, String login, String name, String email) {
    }

    record GitHubEmailResponse(String email, boolean primary, boolean verified) {
    }

    public record GitHubIdentity(String subject, String email, String displayName, String username) {
    }
}

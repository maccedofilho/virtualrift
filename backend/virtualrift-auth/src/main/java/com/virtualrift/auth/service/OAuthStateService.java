package com.virtualrift.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualrift.auth.config.OAuthConfig;
import com.virtualrift.auth.exception.OAuthCallbackException;
import com.virtualrift.auth.exception.OAuthConfigurationException;
import com.virtualrift.auth.exception.OAuthRedirectUriException;
import com.virtualrift.auth.model.OAuthProvider;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class OAuthStateService {

    private final OAuthConfig config;
    private final ObjectMapper objectMapper;

    public OAuthStateService(OAuthConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public String createState(OAuthProvider provider, String redirectUri) {
        validateRedirectUri(redirectUri);

        if (config.getStateSecret() == null || config.getStateSecret().isBlank()) {
            throw new OAuthConfigurationException("OAuth state secret is not configured");
        }

        OAuthStatePayload payload = new OAuthStatePayload(
                provider.wireValue(),
                redirectUri,
                Instant.now().plusSeconds(config.getStateTtlSeconds()).getEpochSecond(),
                UUID.randomUUID().toString()
        );

        try {
            String encodedPayload = encodeBase64Url(objectMapper.writeValueAsBytes(payload));
            String signature = sign(encodedPayload);
            return encodedPayload + "." + signature;
        } catch (JsonProcessingException e) {
            throw new OAuthCallbackException("Unable to serialize OAuth state payload");
        }
    }

    public OAuthStatePayload parseState(String state) {
        if (state == null || state.isBlank()) {
            throw new OAuthCallbackException("OAuth state is missing");
        }

        String[] parts = state.split("\\.");
        if (parts.length != 2) {
            throw new OAuthCallbackException("OAuth state is malformed");
        }

        String encodedPayload = parts[0];
        String signature = parts[1];

        if (!MessageDigest.isEqual(sign(encodedPayload).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new OAuthCallbackException("OAuth state signature is invalid");
        }

        try {
            OAuthStatePayload payload = objectMapper.readValue(decodeBase64Url(encodedPayload), OAuthStatePayload.class);
            if (payload.expiresAtEpochSeconds() < Instant.now().getEpochSecond()) {
                throw new OAuthCallbackException("OAuth state has expired");
            }

            validateRedirectUri(payload.redirectUri());
            return payload;
        } catch (IOException e) {
            throw new OAuthCallbackException("OAuth state payload is invalid");
        }
    }

    public void validateRedirectUri(String redirectUri) {
        URI parsed;
        try {
            parsed = new URI(redirectUri);
        } catch (URISyntaxException e) {
            throw new OAuthRedirectUriException("Redirect URI is not a valid URI");
        }

        if (parsed.getScheme() == null || (!parsed.getScheme().equals("http") && !parsed.getScheme().equals("https"))) {
            throw new OAuthRedirectUriException("Redirect URI must use http or https");
        }

        if (parsed.getHost() == null || parsed.getHost().isBlank()) {
            throw new OAuthRedirectUriException("Redirect URI host is missing");
        }

        if (parsed.getUserInfo() != null) {
            throw new OAuthRedirectUriException("Redirect URI must not contain user info");
        }

        String origin = parsed.getScheme() + "://" + parsed.getHost() + resolvePortSuffix(parsed);
        boolean allowed = config.getAllowedRedirectOrigins().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::trimTrailingSlash)
                .anyMatch(allowedOrigin -> allowedOrigin.equalsIgnoreCase(origin));

        if (!allowed) {
            throw new OAuthRedirectUriException("Redirect URI origin is not allowed");
        }
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.getStateSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return encodeBase64Url(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new OAuthConfigurationException("OAuth state signing is not available");
        }
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String resolvePortSuffix(URI uri) {
        if (uri.getPort() == -1) {
            return "";
        }
        return ":" + uri.getPort();
    }

    private String encodeBase64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] decodeBase64Url(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    public record OAuthStatePayload(
            String provider,
            String redirectUri,
            long expiresAtEpochSeconds,
            String nonce
    ) {
    }
}

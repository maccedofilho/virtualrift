package com.virtualrift.tenant.service;

import com.virtualrift.tenant.dto.RepositoryCredentialsRequest;
import com.virtualrift.tenant.dto.RepositoryCredentialsSummaryResponse;
import com.virtualrift.tenant.exception.InvalidScanTargetConfigurationException;
import com.virtualrift.tenant.model.RepositoryAuthenticationMode;
import com.virtualrift.tenant.model.ScanTarget;
import com.virtualrift.tenant.model.TargetType;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class RepositoryCredentialsService {

    private static final Pattern HEADER_NAME_PATTERN = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");
    private static final int MAX_FIELD_LENGTH = 255;
    private static final int MAX_SECRET_LENGTH = 4096;

    private final RepositoryCredentialCipher credentialCipher;

    public RepositoryCredentialsService(RepositoryCredentialCipher credentialCipher) {
        this.credentialCipher = credentialCipher;
    }

    public PersistedRepositoryCredentials prepareForStorage(RepositoryCredentialsRequest request) {
        if (request == null || request.mode() == null || request.mode() == RepositoryAuthenticationMode.NONE) {
            return PersistedRepositoryCredentials.none();
        }

        return switch (request.mode()) {
            case NONE -> PersistedRepositoryCredentials.none();
            case BEARER_TOKEN -> new PersistedRepositoryCredentials(
                    RepositoryAuthenticationMode.BEARER_TOKEN,
                    null,
                    null,
                    credentialCipher.encrypt(requireSecret(request.secret(), "Bearer token"))
            );
            case BASIC -> new PersistedRepositoryCredentials(
                    RepositoryAuthenticationMode.BASIC,
                    requireField(request.username(), "Repository username"),
                    null,
                    credentialCipher.encrypt(requireSecret(request.secret(), "Repository password"))
            );
            case CUSTOM_HEADER -> new PersistedRepositoryCredentials(
                    RepositoryAuthenticationMode.CUSTOM_HEADER,
                    null,
                    requireHeaderName(request.headerName()),
                    credentialCipher.encrypt(requireSecret(request.secret(), "Repository header value"))
            );
        };
    }

    public RepositoryCredentialsSummaryResponse summarize(ScanTarget scanTarget) {
        if (scanTarget.getType() != TargetType.REPOSITORY) {
            return null;
        }

        RepositoryAuthenticationMode mode = scanTarget.getRepositoryAuthMode() == null
                ? RepositoryAuthenticationMode.NONE
                : scanTarget.getRepositoryAuthMode();
        boolean configured = mode != RepositoryAuthenticationMode.NONE
                && scanTarget.getRepositoryAuthSecretCiphertext() != null
                && !scanTarget.getRepositoryAuthSecretCiphertext().isBlank();

        return new RepositoryCredentialsSummaryResponse(
                mode,
                configured,
                scanTarget.getRepositoryAuthUsername(),
                scanTarget.getRepositoryAuthHeaderName()
        );
    }

    public Map<String, String> resolveHeaders(ScanTarget scanTarget) {
        RepositoryAuthenticationMode mode = scanTarget.getRepositoryAuthMode();
        if (mode == null || mode == RepositoryAuthenticationMode.NONE) {
            return Map.of();
        }

        String secret = credentialCipher.decrypt(scanTarget.getRepositoryAuthSecretCiphertext());
        if (secret == null || secret.isBlank()) {
            return Map.of();
        }

        return switch (mode) {
            case NONE -> Map.of();
            case BEARER_TOKEN -> Map.of("Authorization", "Bearer " + secret);
            case BASIC -> Map.of(
                    "Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(
                            (scanTarget.getRepositoryAuthUsername() + ":" + secret).getBytes(StandardCharsets.UTF_8)
                    )
            );
            case CUSTOM_HEADER -> Map.of(scanTarget.getRepositoryAuthHeaderName(), secret);
        };
    }

    private String requireField(String value, String label) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new InvalidScanTargetConfigurationException(label + " is required");
        }
        if (normalized.length() > MAX_FIELD_LENGTH) {
            throw new InvalidScanTargetConfigurationException(label + " is too long");
        }
        return normalized;
    }

    private String requireSecret(String value, String label) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new InvalidScanTargetConfigurationException(label + " is required");
        }
        if (normalized.length() > MAX_SECRET_LENGTH) {
            throw new InvalidScanTargetConfigurationException(label + " is too long");
        }
        return normalized;
    }

    private String requireHeaderName(String value) {
        String normalized = requireField(value, "Repository header name");
        if (!HEADER_NAME_PATTERN.matcher(normalized).matches()) {
            throw new InvalidScanTargetConfigurationException("Repository header name is invalid");
        }
        return normalized;
    }

    public record PersistedRepositoryCredentials(
            RepositoryAuthenticationMode mode,
            String username,
            String headerName,
            String encryptedSecret
    ) {
        static PersistedRepositoryCredentials none() {
            return new PersistedRepositoryCredentials(RepositoryAuthenticationMode.NONE, null, null, null);
        }
    }
}

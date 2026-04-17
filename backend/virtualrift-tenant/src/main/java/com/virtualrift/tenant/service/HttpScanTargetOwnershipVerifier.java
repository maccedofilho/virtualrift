package com.virtualrift.tenant.service;

import com.virtualrift.tenant.model.ScanTarget;
import com.virtualrift.tenant.model.TargetType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

@Component
public class HttpScanTargetOwnershipVerifier implements ScanTargetOwnershipVerifier {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final String WELL_KNOWN_PATH = "/.well-known/virtualrift-verification.txt";

    private final HttpClient httpClient;
    private final boolean allowPrivateAddresses;

    public HttpScanTargetOwnershipVerifier() {
        this(HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), false);
    }

    HttpScanTargetOwnershipVerifier(HttpClient httpClient, boolean allowPrivateAddresses) {
        this.httpClient = httpClient;
        this.allowPrivateAddresses = allowPrivateAddresses;
    }

    @Override
    public ScanTargetOwnershipVerificationResult verify(ScanTarget scanTarget) {
        Optional<URI> verificationUri = verificationUri(scanTarget);
        if (verificationUri.isEmpty()) {
            return ScanTargetOwnershipVerificationResult.failed("verification method is not supported for target type");
        }
        if (!isAllowedVerificationUri(verificationUri.get())) {
            return ScanTargetOwnershipVerificationResult.failed("verification URL is not allowed");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(verificationUri.get())
                    .GET()
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "text/plain")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return ScanTargetOwnershipVerificationResult.failed("verification file was not reachable");
            }
            if (!response.body().contains(scanTarget.getVerificationToken())) {
                return ScanTargetOwnershipVerificationResult.failed("verification token was not found");
            }
            return ScanTargetOwnershipVerificationResult.success();
        } catch (IOException e) {
            return ScanTargetOwnershipVerificationResult.failed("verification request failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ScanTargetOwnershipVerificationResult.failed("verification request was interrupted");
        } catch (IllegalArgumentException e) {
            return ScanTargetOwnershipVerificationResult.failed("verification URL is invalid");
        }
    }

    private Optional<URI> verificationUri(ScanTarget scanTarget) {
        return switch (scanTarget.getType()) {
            case URL, API_SPEC -> webVerificationUri(scanTarget.getTarget());
            case REPOSITORY -> repositoryVerificationUri(scanTarget.getTarget());
            case IP_RANGE -> Optional.empty();
        };
    }

    private Optional<URI> webVerificationUri(String target) {
        Optional<URI> parsed = parseUri(target);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }

        URI uri = parsed.get();
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return Optional.empty();
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Optional.empty();
        }

        return Optional.of(URI.create(scheme + "://" + authority(uri) + WELL_KNOWN_PATH));
    }

    private Optional<URI> repositoryVerificationUri(String target) {
        Optional<URI> parsed = parseUri(target);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }

        URI uri = parsed.get();
        String host = uri.getHost();
        String path = normalizeRepositoryPath(uri.getPath());
        if (host == null || path.isBlank()) {
            return Optional.empty();
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        String[] segments = Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);

        if (normalizedHost.equals("github.com") && segments.length >= 2) {
            return Optional.of(URI.create("https://raw.githubusercontent.com/"
                    + encodePath(segments[0]) + "/" + encodePath(segments[1])
                    + "/HEAD" + WELL_KNOWN_PATH));
        }
        if (normalizedHost.equals("gitlab.com") && segments.length >= 2) {
            return Optional.of(URI.create("https://gitlab.com/" + encodePath(path)
                    + "/-/raw/HEAD" + WELL_KNOWN_PATH));
        }
        if (normalizedHost.equals("bitbucket.org") && segments.length >= 2) {
            return Optional.of(URI.create("https://bitbucket.org/"
                    + encodePath(segments[0]) + "/" + encodePath(segments[1])
                    + "/raw/HEAD" + WELL_KNOWN_PATH));
        }

        return Optional.empty();
    }

    private Optional<URI> parseUri(String target) {
        if (target == null || target.isBlank()) {
            return Optional.empty();
        }
        try {
            String normalized = target.trim();
            return Optional.of(URI.create(normalized.contains("://") ? normalized : "https://" + normalized));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private boolean isAllowedVerificationUri(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || host.isBlank()) {
            return false;
        }
        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if (!normalizedScheme.equals("http") && !normalizedScheme.equals("https")) {
            return false;
        }
        if (allowPrivateAddresses) {
            return true;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.equals("localhost") || normalizedHost.endsWith(".localhost")) {
            return false;
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String authority(URI uri) {
        String host = uri.getHost();
        String renderedHost = host.contains(":") ? "[" + host + "]" : host;
        if (uri.getPort() == -1) {
            return renderedHost;
        }
        return renderedHost + ":" + uri.getPort();
    }

    private String normalizeRepositoryPath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.endsWith(".git") ? path.substring(0, path.length() - 4) : path;
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String encodePath(String value) {
        return Arrays.stream(value.split("/"))
                .filter(segment -> !segment.isBlank())
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .reduce((left, right) -> left + "/" + right)
                .orElse("");
    }
}

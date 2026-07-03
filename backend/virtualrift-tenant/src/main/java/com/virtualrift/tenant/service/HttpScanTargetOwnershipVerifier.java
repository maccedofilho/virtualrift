package com.virtualrift.tenant.service;

import com.virtualrift.tenant.config.RepositoryCredentialsConfig;
import com.virtualrift.tenant.dto.ScanTargetVerificationGuideResponse;
import com.virtualrift.tenant.model.ScanTarget;
import com.virtualrift.tenant.model.ScanTargetVerificationMethod;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class HttpScanTargetOwnershipVerifier implements ScanTargetOwnershipVerifier {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final String WELL_KNOWN_PATH = "/.well-known/virtualrift-verification.txt";
    private static final String DNS_TXT_PREFIX = "_virtualrift-verification.";
    private static final List<String> REPOSITORY_REFS = List.of("HEAD", "main", "master");

    private final HttpClient httpClient;
    private final DnsTxtRecordResolver dnsTxtRecordResolver;
    private final boolean allowPrivateAddresses;
    private final RepositoryCredentialsService repositoryCredentialsService;

    public HttpScanTargetOwnershipVerifier() {
        this(HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), new JndiDnsTxtRecordResolver(), false, defaultRepositoryCredentialsService());
    }

    HttpScanTargetOwnershipVerifier(HttpClient httpClient, boolean allowPrivateAddresses) {
        this(httpClient, new JndiDnsTxtRecordResolver(), allowPrivateAddresses, defaultRepositoryCredentialsService());
    }

    HttpScanTargetOwnershipVerifier(
            HttpClient httpClient,
            DnsTxtRecordResolver dnsTxtRecordResolver,
            boolean allowPrivateAddresses
    ) {
        this(httpClient, dnsTxtRecordResolver, allowPrivateAddresses, defaultRepositoryCredentialsService());
    }

    HttpScanTargetOwnershipVerifier(
            HttpClient httpClient,
            DnsTxtRecordResolver dnsTxtRecordResolver,
            boolean allowPrivateAddresses,
            RepositoryCredentialsService repositoryCredentialsService
    ) {
        this.httpClient = httpClient;
        this.dnsTxtRecordResolver = dnsTxtRecordResolver;
        this.allowPrivateAddresses = allowPrivateAddresses;
        this.repositoryCredentialsService = repositoryCredentialsService;
    }

    @Override
    public ScanTargetOwnershipVerificationResult verify(ScanTarget scanTarget) {
        return switch (scanTarget.getType()) {
            case URL, API_SPEC -> verifyWebTarget(scanTarget);
            case REPOSITORY -> verifyRepositoryTarget(scanTarget);
            case IP_RANGE -> ScanTargetOwnershipVerificationResult.failed(
                    "IP range ownership requires manual review before NETWORK scans can run"
            );
        };
    }

    @Override
    public ScanTargetVerificationGuideResponse describe(ScanTarget scanTarget) {
        return switch (scanTarget.getType()) {
            case URL, API_SPEC -> webVerificationGuide(scanTarget);
            case REPOSITORY -> repositoryVerificationGuide(scanTarget);
            case IP_RANGE -> ipRangeVerificationGuide(scanTarget);
        };
    }

    private ScanTargetOwnershipVerificationResult verifyWebTarget(ScanTarget scanTarget) {
        Optional<URI> verificationUri = webVerificationUri(scanTarget.getTarget());
        if (verificationUri.isEmpty()) {
            return ScanTargetOwnershipVerificationResult.failed("verification target URL is invalid");
        }
        if (!isAllowedVerificationUri(verificationUri.get())) {
            return ScanTargetOwnershipVerificationResult.failed("verification URL is not allowed");
        }

        VerificationProbeResult httpProbe = verifyHttpToken(verificationUri.get(), scanTarget.getVerificationToken(), Map.of());
        if (httpProbe.matched()) {
            return ScanTargetOwnershipVerificationResult.success();
        }

        Optional<String> host = extractHost(scanTarget.getTarget());
        if (host.isEmpty()) {
            return ScanTargetOwnershipVerificationResult.failed(httpProbe.detail());
        }

        VerificationProbeResult dnsProbe = verifyDnsTxtToken(host.get(), scanTarget.getVerificationToken());
        if (dnsProbe.matched()) {
            return ScanTargetOwnershipVerificationResult.success();
        }

        if (httpProbe.tokenMissing() || dnsProbe.tokenMissing()) {
            return ScanTargetOwnershipVerificationResult.failed(
                    "verification token was not found in the HTTP well-known file or DNS TXT record"
            );
        }
        return ScanTargetOwnershipVerificationResult.failed(
                "ownership proof was not reachable via the HTTP well-known file or DNS TXT record"
        );
    }

    private ScanTargetOwnershipVerificationResult verifyRepositoryTarget(ScanTarget scanTarget) {
        List<URI> verificationUris = repositoryVerificationUris(scanTarget.getTarget());
        if (verificationUris.isEmpty()) {
            return ScanTargetOwnershipVerificationResult.failed("repository verification method is not supported for this target");
        }

        Map<String, String> headers = repositoryCredentialsService.resolveHeaders(scanTarget);
        boolean tokenMissing = false;
        for (URI verificationUri : verificationUris) {
            if (!isAllowedVerificationUri(verificationUri)) {
                return ScanTargetOwnershipVerificationResult.failed("verification URL is not allowed");
            }

            VerificationProbeResult probe = verifyHttpToken(verificationUri, scanTarget.getVerificationToken(), headers);
            if (probe.matched()) {
                return ScanTargetOwnershipVerificationResult.success();
            }
            tokenMissing = tokenMissing || probe.tokenMissing();
        }

        if (tokenMissing) {
            return ScanTargetOwnershipVerificationResult.failed(
                    "verification token was not found in supported repository raw file locations"
            );
        }
        return ScanTargetOwnershipVerificationResult.failed(
                "verification file was not reachable in supported repository raw file locations"
        );
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

    private ScanTargetVerificationGuideResponse webVerificationGuide(ScanTarget scanTarget) {
        String host = extractHost(scanTarget.getTarget()).orElse("seu-dominio");
        return new ScanTargetVerificationGuideResponse(
                true,
                ScanTargetVerificationMethod.HTTP_WELL_KNOWN_OR_DNS_TXT,
                "https://" + host + WELL_KNOWN_PATH,
                List.of(
                        "Publique o token em texto puro no arquivo " + "https://" + host + WELL_KNOWN_PATH + ".",
                        "Como alternativa, crie um registro TXT em " + dnsTxtRecordName(host) + " contendo o token.",
                        "Depois de aplicar uma das provas de posse, execute a verificacao novamente."
                )
        );
    }

    private ScanTargetVerificationGuideResponse repositoryVerificationGuide(ScanTarget scanTarget) {
        return new ScanTargetVerificationGuideResponse(
                true,
                ScanTargetVerificationMethod.REPOSITORY_RAW_FILE,
                ".well-known/virtualrift-verification.txt",
                List.of(
                        "Adicione o token em texto puro ao arquivo .well-known/virtualrift-verification.txt na branch default do repositorio.",
                        "Garanta que o arquivo esteja acessivel no endpoint raw do seu provedor Git.",
                        "Depois de publicar o arquivo, execute a verificacao novamente."
                )
        );
    }

    private ScanTargetVerificationGuideResponse ipRangeVerificationGuide(ScanTarget scanTarget) {
        return new ScanTargetVerificationGuideResponse(
                false,
                ScanTargetVerificationMethod.MANUAL_REVIEW,
                null,
                List.of(
                        "Faixas IP exigem revisao manual antes de habilitar scans NETWORK.",
                        "Guarde o token deste alvo e prepare evidencias de posse do bloco, como WHOIS, provedor cloud ou carta de autorizacao.",
                        "Aprovacoes manuais ainda nao estao automatizadas no painel."
                )
        );
    }

    private List<URI> repositoryVerificationUris(String target) {
        Optional<URI> parsed = parseUri(target);
        if (parsed.isEmpty()) {
            return List.of();
        }

        URI uri = parsed.get();
        String host = uri.getHost();
        String path = normalizeRepositoryPath(uri.getPath());
        if (host == null || path.isBlank()) {
            return List.of();
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        String[] segments = Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
        String scheme = repositoryVerificationScheme(uri);
        String authority = authority(uri);
        String encodedPath = encodePath(path);
        LinkedHashSet<URI> candidates = new LinkedHashSet<>();

        if (normalizedHost.equals("github.com") && segments.length >= 2) {
            for (String ref : REPOSITORY_REFS) {
                candidates.add(URI.create("https://raw.githubusercontent.com/"
                        + encodePath(segments[0]) + "/" + encodePath(segments[1])
                        + "/" + ref + WELL_KNOWN_PATH));
            }
        }

        if (segments.length >= 2) {
            for (String ref : REPOSITORY_REFS) {
                candidates.add(URI.create(scheme + "://" + authority + "/" + encodedPath
                        + "/-/raw/" + ref + WELL_KNOWN_PATH));
                candidates.add(URI.create(scheme + "://" + authority + "/"
                        + encodePath(segments[0]) + "/" + encodePath(segments[1])
                        + "/raw/" + ref + WELL_KNOWN_PATH));
                candidates.add(URI.create(scheme + "://" + authority + "/"
                        + encodePath(segments[0]) + "/" + encodePath(segments[1])
                        + "/raw/branch/" + ref + WELL_KNOWN_PATH));
            }
        }

        return new ArrayList<>(candidates);
    }

    private Optional<URI> parseUri(String target) {
        if (target == null || target.isBlank()) {
            return Optional.empty();
        }
        try {
            String normalized = normalizeUriValue(target.trim());
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

    private Optional<String> extractHost(String target) {
        return parseUri(target)
                .map(URI::getHost)
                .filter(host -> host != null && !host.isBlank())
                .map(host -> host.toLowerCase(Locale.ROOT));
    }

    private VerificationProbeResult verifyHttpToken(URI verificationUri, String token, Map<String, String> headers) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(verificationUri)
                    .GET()
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "text/plain");
            if (headers != null) {
                headers.forEach(requestBuilder::header);
            }
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return VerificationProbeResult.unreachableFailure("repository credentials were rejected while checking the verification file");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return VerificationProbeResult.unreachableFailure("verification file was not reachable");
            }
            if (!response.body().contains(token)) {
                return VerificationProbeResult.tokenMissingFailure("verification token was not found");
            }
            return VerificationProbeResult.success();
        } catch (IOException e) {
            return VerificationProbeResult.unreachableFailure("verification request failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return VerificationProbeResult.unreachableFailure("verification request was interrupted");
        } catch (IllegalArgumentException e) {
            return VerificationProbeResult.unreachableFailure("verification URL is invalid");
        }
    }

    private VerificationProbeResult verifyDnsTxtToken(String host, String token) {
        try {
            List<String> values = dnsTxtRecordResolver.resolve(dnsTxtRecordName(host));
            if (values.isEmpty()) {
                return VerificationProbeResult.unreachableFailure("DNS TXT record was not found");
            }
            for (String value : values) {
                if (stripDnsQuotes(value).contains(token)) {
                    return VerificationProbeResult.success();
                }
            }
            return VerificationProbeResult.tokenMissingFailure("verification token was not found");
        } catch (IOException exception) {
            return VerificationProbeResult.unreachableFailure("DNS TXT lookup failed");
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

    private String repositoryVerificationScheme(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || scheme.isBlank() || scheme.equalsIgnoreCase("ssh")) {
            return "https";
        }
        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        return normalizedScheme.equals("http") ? "http" : "https";
    }

    private String normalizeUriValue(String value) {
        if (value.matches("^[^@/\\s]+@[^:/\\s]+:.+$")) {
            int separator = value.indexOf(':');
            return "ssh://" + value.substring(0, separator) + "/" + value.substring(separator + 1);
        }
        return value;
    }

    private String dnsTxtRecordName(String host) {
        return DNS_TXT_PREFIX + host.toLowerCase(Locale.ROOT);
    }

    private String stripDnsQuotes(String value) {
        return value == null ? "" : value.replace("\"", "");
    }

    private String encodePath(String value) {
        return Arrays.stream(value.split("/"))
                .filter(segment -> !segment.isBlank())
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .reduce((left, right) -> left + "/" + right)
                .orElse("");
    }

    private static RepositoryCredentialsService defaultRepositoryCredentialsService() {
        RepositoryCredentialsConfig config = new RepositoryCredentialsConfig();
        return new RepositoryCredentialsService(new RepositoryCredentialCipher(config));
    }

    private record VerificationProbeResult(boolean matched, boolean tokenMissing, String detail) {

        private static VerificationProbeResult success() {
            return new VerificationProbeResult(true, false, null);
        }

        private static VerificationProbeResult tokenMissingFailure(String detail) {
            return new VerificationProbeResult(false, true, detail);
        }

        private static VerificationProbeResult unreachableFailure(String detail) {
            return new VerificationProbeResult(false, false, detail);
        }
    }
}

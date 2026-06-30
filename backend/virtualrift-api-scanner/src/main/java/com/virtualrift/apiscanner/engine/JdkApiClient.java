package com.virtualrift.apiscanner.engine;

import com.virtualrift.apiscanner.config.ApiScannerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class JdkApiClient implements ApiClient {

    private final java.net.http.HttpClient httpClient;
    private final ApiScannerProperties properties;
    private final Map<String, String> defaultHeaders;
    private final Map<String, String> defaultCookies;

    @Autowired
    public JdkApiClient(ApiScannerProperties properties) {
        this(
                java.net.http.HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                        .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                        .build(),
                properties,
                Map.of(),
                Map.of()
        );
    }

    private JdkApiClient(java.net.http.HttpClient httpClient,
                         ApiScannerProperties properties,
                         Map<String, String> defaultHeaders,
                         Map<String, String> defaultCookies) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.defaultHeaders = Map.copyOf(defaultHeaders);
        this.defaultCookies = Map.copyOf(defaultCookies);
    }

    @Override
    public HttpResponse<String> sendRequest(String url, String method, Map<String, String> headers) {
        return execute(requestBuilder(url, headers).method(normalizeMethod(method), HttpRequest.BodyPublishers.noBody()).build());
    }

    @Override
    public HttpResponse<String> sendRequest(String url, String method) {
        return sendRequest(url, method, Map.of());
    }

    @Override
    public HttpResponse<String> sendRequestWithBody(String url, String method, String body) {
        return sendRequestWithBody(url, method, body, Map.of());
    }

    @Override
    public HttpResponse<String> sendRequestWithBody(String url, String method, String body, Map<String, String> headers) {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        return execute(requestBuilder(url, headers).method(normalizeMethod(method), publisher).build());
    }

    @Override
    public HttpResponse<String> sendRequestWithAuth(String url, String method, String token) {
        return sendRequest(url, method, Map.of("Authorization", "Bearer " + token));
    }

    @Override
    public ApiClient withContext(Map<String, String> headers, Map<String, String> cookies) {
        return new JdkApiClient(
                httpClient,
                properties,
                merge(defaultHeaders, headers),
                merge(defaultCookies, cookies)
        );
    }

    private HttpRequest.Builder requestBuilder(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout())
                .header("User-Agent", properties.getUserAgent())
                .header("Accept", "application/json, text/plain, */*");

        merge(defaultHeaders, headers).forEach((name, value) -> {
            if (name != null && !name.isBlank() && value != null && !value.isBlank()) {
                builder.header(name, value);
            }
        });

        if (!defaultCookies.isEmpty()) {
            builder.header("Cookie", toCookieHeader(defaultCookies));
        }

        return builder;
    }

    private HttpResponse<String> execute(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | IllegalArgumentException e) {
            return emptyResponse(request, 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return emptyResponse(request, 0);
        }
    }

    private Duration timeout() {
        return Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds()));
    }

    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "GET";
        }
        return method.toUpperCase();
    }

    private HttpResponse<String> emptyResponse(HttpRequest request, int statusCode) {
        return new SimpleHttpResponse(statusCode, "", HttpHeaders.of(Map.of(), (name, value) -> true), request);
    }

    private Map<String, String> merge(Map<String, String> base, Map<String, String> overrides) {
        if ((base == null || base.isEmpty()) && (overrides == null || overrides.isEmpty())) {
            return Map.of();
        }

        Map<String, String> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        if (overrides != null) {
            merged.putAll(overrides);
        }
        return Map.copyOf(merged);
    }

    private String toCookieHeader(Map<String, String> cookies) {
        return cookies.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
    }

    private record SimpleHttpResponse(
            int statusCode,
            String body,
            HttpHeaders headers,
            HttpRequest request
    ) implements HttpResponse<String> {

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }
    }
}

package com.virtualrift.webscanner.engine;

import com.virtualrift.webscanner.config.WebScannerProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JdkWebScannerHttpClient implements HttpClient {

    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
            "<script[^>]*src=[\"']([^\"']+)[\"'][^>]*>|<script[^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final java.net.http.HttpClient client;
    private final WebScannerProperties properties;

    public JdkWebScannerHttpClient(WebScannerProperties properties) {
        this.properties = properties;
        this.client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(timeout())
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public Optional<String> sendRequest(String url, String payload) {
        return get(appendQuery(url, payload), Map.of());
    }

    @Override
    public Optional<String> getPage(String url) {
        return get(url, Map.of());
    }

    @Override
    public Optional<String> sendRequestWithCookie(String url, String payload, String cookieName, String cookieValue) {
        return get(appendQuery(url, payload), Map.of("Cookie", cookieName + "=" + cookieValue));
    }

    @Override
    public Optional<String> sendRequestWithCookie(String url, String payload) {
        return sendRequestWithCookie(url, payload, "virtualrift", payload);
    }

    @Override
    public Optional<String> sendRequestWithHeader(String url, String payload, String headerName, String headerValue) {
        return get(appendQuery(url, payload), Map.of(headerName, headerValue));
    }

    @Override
    public Optional<String> sendRequestWithHeader(String url, String payload) {
        return sendRequestWithHeader(url, payload, "X-VirtualRift-Scan", payload);
    }

    @Override
    public Optional<String> sendJson(String url, String jsonPayload) {
        HttpRequest.Builder builder = requestBuilder(url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload == null ? "" : jsonPayload));
        return execute(builder.build());
    }

    @Override
    public List<String> fetchJavaScript(String url) {
        Optional<String> page = getPage(url);
        if (page.isEmpty()) {
            return List.of();
        }

        List<String> scripts = new ArrayList<>();
        Matcher matcher = SCRIPT_PATTERN.matcher(page.get());
        while (matcher.find()) {
            String src = matcher.group(1);
            String inline = matcher.group(2);
            if (src != null && !src.isBlank()) {
                get(resolve(url, src), Map.of()).ifPresent(scripts::add);
            } else if (inline != null && !inline.isBlank()) {
                scripts.add(inline);
            }
        }
        return scripts;
    }

    @Override
    public long measureResponseTime(String url) {
        long start = System.currentTimeMillis();
        getPage(url);
        return System.currentTimeMillis() - start;
    }

    private Optional<String> get(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = requestBuilder(url).GET();
        headers.forEach(builder::header);
        return execute(builder.build());
    }

    private Optional<String> execute(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 500) {
                return Optional.ofNullable(response.body());
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private HttpRequest.Builder requestBuilder(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout())
                .header("User-Agent", properties.getUserAgent());
    }

    private Duration timeout() {
        return Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds()));
    }

    private String appendQuery(String url, String payload) {
        if (payload == null || payload.isBlank()) {
            return url;
        }

        String query = encodeQueryPayload(payload);
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + query;
    }

    private String encodeQueryPayload(String payload) {
        int equalsIndex = payload.indexOf('=');
        if (equalsIndex < 0) {
            return encode(payload);
        }

        String name = payload.substring(0, equalsIndex);
        String value = payload.substring(equalsIndex + 1);
        return encode(name) + "=" + encode(value);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String resolve(String baseUrl, String scriptUrl) {
        return URI.create(baseUrl).resolve(scriptUrl).toString();
    }
}

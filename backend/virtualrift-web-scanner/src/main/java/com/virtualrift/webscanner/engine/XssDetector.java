package com.virtualrift.webscanner.engine;

import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.VulnerabilityFinding;
import com.virtualrift.common.model.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XssDetector {

    private static final Logger log = LoggerFactory.getLogger(XssDetector.class);

    private static final Set<String> INTERNAL_PATTERNS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "::1",
            "192.168.", "10.", "172.16.", "169.254."
    );

    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ONERROR_PATTERN = Pattern.compile("onerror\\s*=\\s*[\"']?[^\"'\\s>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVASCRIPT_PROTOCOL = Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE);
    private static final Pattern ONLOAD_PATTERN = Pattern.compile("onload\\s*=\\s*[\"']?[^\"'\\s>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern INNERHTML_PATTERN = Pattern.compile("\\.innerHTML\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCATION_HASH_PATTERN = Pattern.compile("location\\.hash|location\\.search|window\\.name", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOCUMENT_WRITE_PATTERN = Pattern.compile("document\\.write|document\\.writeln", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVAL_PATTERN = Pattern.compile("\\beval\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern SETTIMEOUT_PATTERN = Pattern.compile("setTimeout\\s*\\(\\s*[\"'`]", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXTCONTENT_PATTERN = Pattern.compile("\\.textContent\\s*=|\\.innerText\\s*=", Pattern.CASE_INSENSITIVE);

    private static final Set<String> DANGEROUS_SOURCES = Set.of(
            "location.hash", "location.search", "location.href",
            "window.name", "document.cookie", "document.URL",
            "document.referrer", "window.location"
    );

    private final HttpClient httpClient;

    public XssDetector(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<VulnerabilityFinding> scan(String targetUrl, String paramName, String payload) {
        validateUrl(targetUrl);
        if (paramName == null || paramName.isBlank()) {
            throw new IllegalArgumentException("paramName cannot be blank");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload cannot be blank");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        Optional<String> response = httpClient.sendRequest(targetUrl, paramName + "=" + payload);

        if (response.isPresent()) {
            String responseBody = response.get();

            if (isReflectedXss(responseBody, payload)) {
                findings.add(createFinding(
                        targetUrl,
                        determineSeverity(payload, responseBody),
                        "Reflected XSS",
                        "Parameter: " + paramName,
                        payload
                ));
            }
        }

        return findings;
    }

    public List<VulnerabilityFinding> analyzeJavaScript(String targetUrl) {
        validateUrl(targetUrl);

        List<VulnerabilityFinding> findings = new ArrayList<>();
        List<String> scripts = httpClient.fetchJavaScript(targetUrl);

        for (String script : scripts) {
            findings.addAll(analyzeScriptForDomXss(targetUrl, script));
        }

        return findings;
    }

    public List<VulnerabilityFinding> scanStored(String targetUrl, String submitPath, String viewPath, String payload) {
        validateUrl(targetUrl);

        List<VulnerabilityFinding> findings = new ArrayList<>();

        String submitUrl = targetUrl + submitPath;
        httpClient.sendRequest(submitUrl, payload);

        String viewUrl = targetUrl + viewPath;
        Optional<String> response = httpClient.getPage(viewUrl);

        if (response.isPresent() && response.get().contains(payload)) {
            findings.add(createFinding(
                    viewUrl,
                    Severity.CRITICAL,
                    "Stored XSS",
                    "Stored at: " + submitPath + ", reflected at: " + viewPath,
                    payload
            ));
        }

        return findings;
    }

    private List<VulnerabilityFinding> analyzeScriptForDomXss(String targetUrl, String script) {
        List<VulnerabilityFinding> findings = new ArrayList<>();

        if (isSafeDomUsage(script)) {
            return findings;
        }

        if (INNERHTML_PATTERN.matcher(script).find() && hasDangerousSource(script)) {
            findings.add(createFinding(
                    targetUrl,
                    Severity.HIGH,
                    "DOM-based XSS",
                    "innerHTML sink with user input source",
                    script
            ));
        }

        if (LOCATION_HASH_PATTERN.matcher(script).find() && hasDangerousSink(script)) {
            findings.add(createFinding(
                    targetUrl,
                    Severity.HIGH,
                    "DOM-based XSS",
                    "location.hash as source",
                    script
            ));
        }

        if (DOCUMENT_WRITE_PATTERN.matcher(script).find() && hasDangerousSource(script)) {
            findings.add(createFinding(
                    targetUrl,
                    Severity.HIGH,
                    "DOM-based XSS",
                    "document.write with user input",
                    script
            ));
        }

        if (EVAL_PATTERN.matcher(script).find() && hasDangerousSource(script)) {
            findings.add(createFinding(
                    targetUrl,
                    Severity.CRITICAL,
                    "DOM-based XSS",
                    "eval with user input source",
                    script
            ));
        }

        if (SETTIMEOUT_PATTERN.matcher(script).find() && hasDangerousSource(script)) {
            findings.add(createFinding(
                    targetUrl,
                    Severity.HIGH,
                    "DOM-based XSS",
                    "setTimeout with user input",
                    script
            ));
        }

        return findings;
    }

    private boolean isReflectedXss(String response, String payload) {
        if (response == null || response.isBlank()) {
            return false;
        }

        String normalizedPayload = payload.toLowerCase();
        String normalizedResponse = response.toLowerCase();

        if (normalizedResponse.contains(normalizedPayload)) {
            return true;
        }

        String decodedPayload = decodePayload(payload);
        if (!decodedPayload.equals(payload) && normalizedResponse.contains(decodedPayload.toLowerCase())) {
            return true;
        }

        return SCRIPT_PATTERN.matcher(response).find()
                || ONERROR_PATTERN.matcher(response).find()
                || JAVASCRIPT_PROTOCOL.matcher(response).find()
                || ONLOAD_PATTERN.matcher(response).find();
    }

    private boolean isSafeDomUsage(String script) {
        return TEXTCONTENT_PATTERN.matcher(script).find();
    }

    private boolean hasDangerousSource(String script) {
        String lower = script.toLowerCase();
        return DANGEROUS_SOURCES.stream().anyMatch(lower::contains);
    }

    private boolean hasDangerousSink(String script) {
        return INNERHTML_PATTERN.matcher(script).find()
                || EVAL_PATTERN.matcher(script).find()
                || DOCUMENT_WRITE_PATTERN.matcher(script).find()
                || SETTIMEOUT_PATTERN.matcher(script).find();
    }

    private Severity determineSeverity(String payload, String response) {
        if (payload.toLowerCase().contains("document.cookie") ||
            payload.toLowerCase().contains("fetch(") ||
            payload.toLowerCase().contains("xmlhttprequest")) {
            return Severity.CRITICAL;
        }

        if (payload.contains("';alert('XSS');//")) {
            return Severity.CRITICAL;
        }

        if (SCRIPT_PATTERN.matcher(response).find() ||
            ONERROR_PATTERN.matcher(response).find()) {
            return Severity.HIGH;
        }

        if (response.contains(payload.substring(0, Math.min(10, payload.length())) + "...")) {
            return Severity.MEDIUM;
        }

        return Severity.HIGH;
    }

    private String decodePayload(String payload) {
        try {
            return java.net.URLDecoder.decode(payload, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            log.debug("Failed to decode payload (should not happen with UTF-8): {}", payload, e);
            return payload;
        }
    }

    private VulnerabilityFinding createFinding(String url, Severity severity, String title,
                                               String location, String evidence) {
        return VulnerabilityFinding.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new TenantId(UUID.randomUUID()),
                title,
                severity,
                "XSS",
                location + " at " + url,
                evidence,
                Instant.now()
        );
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or blank");
        }

        String lowerUrl = url.toLowerCase();

        for (String pattern : INTERNAL_PATTERNS) {
            if (lowerUrl.contains(pattern)) {
                throw new IllegalArgumentException(
                        "SSRF protection: Cannot scan internal addresses (" + pattern + ")"
                );
            }
        }

        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            throw new IllegalArgumentException("URL must start with http:// or https://");
        }
    }
}

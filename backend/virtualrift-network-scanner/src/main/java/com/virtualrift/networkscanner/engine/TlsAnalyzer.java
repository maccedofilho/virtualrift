package com.virtualrift.networkscanner.engine;

import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.VulnerabilityFinding;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.security.auth.x500.X500Principal;

public class TlsAnalyzer {

    private static final Set<String> INTERNAL_PATTERNS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "::1",
            "192.168.", "10.", "172.16.", "169.254."
    );

    private static final Set<String> SECURE_PROTOCOLS = Set.of("TLSv1.2", "TLSv1.3");
    private static final Set<String> WEAK_PROTOCOLS = Set.of("SSLv3", "SSLv2", "TLSv1", "TLSv1.1", "TLSv1.0");

    private static final Set<String> NULL_CIPHERS = Set.of("NULL", "NULL_WITH_NULL");
    private static final Set<String> ANON_CIPHERS = Set.of("anon", "aNULL", "ADH", "AECDH");
    private static final Set<String> EXPORT_CIPHERS = Set.of("EXPORT", "EXPORT40", "EXPORT56");
    private static final Set<String> RC4_CIPHERS = Set.of("RC4");
    private static final Set<String> DES_CIPHERS = Set.of("DES", "3DES", "DES_CBC");
    private static final Set<String> MD5_CIPHERS = Set.of("MD5");

    private static final Set<String> PFS_KEY_EXCHANGE = Set.of("ECDHE", "DHE", "EDH");

    private final TlsConnection tlsConnection;

    public TlsAnalyzer(TlsConnection tlsConnection) {
        this.tlsConnection = tlsConnection;
    }

    public List<VulnerabilityFinding> analyzeCertificate(String host, int port) {
        validateHost(host);
        validatePort(port);

        List<VulnerabilityFinding> findings = new ArrayList<>();

        Optional<X509Certificate> certOpt = tlsConnection.fetchCertificate(host, port);
        if (certOpt.isEmpty()) {
            return findings;
        }

        X509Certificate cert = certOpt.get();

        findings.addAll(checkCertificateValidity(cert, host, port));

        return findings;
    }

    public List<VulnerabilityFinding> analyzeProtocols(String host, int port) {
        validateHost(host);
        validatePort(port);

        List<VulnerabilityFinding> findings = new ArrayList<>();

        List<String> protocols = tlsConnection.getSupportedProtocols(host, port);

        for (String protocol : protocols) {
            if (WEAK_PROTOCOLS.contains(protocol)) {
                Severity severity = protocol.startsWith("SSL") ? Severity.CRITICAL : Severity.HIGH;
                findings.add(createFinding(
                        host + ":" + port,
                        severity,
                        "Weak TLS Protocol",
                        "Protocol: " + protocol,
                        "Server supports " + protocol + " which is deprecated and insecure"
                ));
            }
        }

        if (protocols.isEmpty()) {
            findings.add(createFinding(
                    host + ":" + port,
                    Severity.MEDIUM,
                    "TLS Protocol Detection Failed",
                    "Port: " + port,
                    "Could not detect supported TLS protocols"
            ));
        }

        return findings;
    }

    public List<VulnerabilityFinding> analyzeCiphers(String host, int port) {
        validateHost(host);
        validatePort(port);

        List<VulnerabilityFinding> findings = new ArrayList<>();

        List<String> ciphers = tlsConnection.getCipherSuites(host, port);

        for (String cipher : ciphers) {
            String upperCipher = cipher.toUpperCase();

            if (containsAny(upperCipher, NULL_CIPHERS)) {
                findings.add(createFinding(
                        host + ":" + port,
                        Severity.CRITICAL,
                        "NULL Cipher Suite",
                        "Cipher: " + cipher,
                        "Cipher offers no encryption"
                ));
            }

            if (containsAny(upperCipher, ANON_CIPHERS)) {
                findings.add(createFinding(
                        host + ":" + port,
                        Severity.HIGH,
                        "Anonymous Cipher Suite",
                        "Cipher: " + cipher,
                        "Cipher provides no authentication (anonymous)"
                ));
            }

            if (containsAny(upperCipher, EXPORT_CIPHERS)) {
                findings.add(createFinding(
                        host + ":" + port,
                        Severity.HIGH,
                        "Export-Grade Cipher Suite",
                        "Cipher: " + cipher,
                        "Export-grade encryption is weak and deprecated"
                ));
            }

            if (containsAny(upperCipher, RC4_CIPHERS)) {
                findings.add(createFinding(
                        host + ":" + port,
                        Severity.HIGH,
                        "RC4 Cipher Suite",
                        "Cipher: " + cipher,
                        "RC4 is broken and should not be used"
                ));
            }

            if (containsAny(upperCipher, DES_CIPHERS)) {
                findings.add(createFinding(
                        host + ":" + port,
                        Severity.HIGH,
                        "DES Cipher Suite",
                        "Cipher: " + cipher,
                        "DES is weak and deprecated"
                ));
            }

            if (containsAny(upperCipher, MD5_CIPHERS)) {
                findings.add(createFinding(
                        host + ":" + port,
                        Severity.MEDIUM,
                        "MD5 Cipher Suite",
                        "Cipher: " + cipher,
                        "MD5 is weak for HMAC"
                ));
            }
        }

        return findings;
    }

    public List<VulnerabilityFinding> analyzeKeyExchange(String host, int port) {
        validateHost(host);
        validatePort(port);

        List<VulnerabilityFinding> findings = new ArrayList<>();

        List<String> keyExchange = tlsConnection.getKeyExchangeMethods(host, port);

        boolean hasPfs = false;
        boolean hasRsaOnly = false;

        for (String method : keyExchange) {
            if (containsAny(method.toUpperCase(), PFS_KEY_EXCHANGE)) {
                hasPfs = true;
            }

            if ("RSA".equals(method)) {
                hasRsaOnly = true;
            }

            if (method.contains("DH_1024") || method.contains("DH_512")) {
                findings.add(createFinding(
                        host + ":" + port,
                        Severity.HIGH,
                        "Weak DH Parameters",
                        "Key exchange: " + method,
                        "Diffie-Hellman parameters are too small"
                ));
            }
        }

        if (hasRsaOnly && !hasPfs) {
            findings.add(createFinding(
                    host + ":" + port,
                    Severity.MEDIUM,
                    "No Forward Secrecy",
                    "Key exchange: RSA",
                    "RSA key exchange provides no perfect forward secrecy"
            ));
        }

        return findings;
    }

    public List<VulnerabilityFinding> analyzeHostname(String host, int port) {
        validateHost(host);
        validatePort(port);

        List<VulnerabilityFinding> findings = new ArrayList<>();

        Optional<X509Certificate> certOpt = tlsConnection.fetchCertificate(host, port);
        if (certOpt.isEmpty()) {
            return findings;
        }

        X509Certificate cert = certOpt.get();

        X500Principal subject = cert.getSubjectX500Principal();
        String subjectName = subject.getName();

        if (!hostnameMatchesCertificate(host, subjectName)) {
            findings.add(createFinding(
                    host + ":" + port,
                    Severity.HIGH,
                    "Certificate Hostname Mismatch",
                    "Certificate: " + subjectName,
                    "Certificate does not match the requested hostname"
            ));
        }

        return findings;
    }

    public List<VulnerabilityFinding> analyzeHsts(String host, int port) {
        validateHost(host);
        validatePort(port);

        List<VulnerabilityFinding> findings = new ArrayList<>();

        List<String> headers = tlsConnection.getHttpHeaders(host, port);

        boolean hasHsts = false;
        int maxAge = 0;

        for (String header : headers) {
            if (header.toLowerCase().startsWith("strict-transport-security:")) {
                hasHsts = true;

                Pattern agePattern = Pattern.compile("max-age=(\\d+)", Pattern.CASE_INSENSITIVE);
                var matcher = agePattern.matcher(header);
                if (matcher.find()) {
                    maxAge = Integer.parseInt(matcher.group(1));
                }
            }
        }

        if (!hasHsts) {
            findings.add(createFinding(
                    host + ":" + port,
                    Severity.MEDIUM,
                    "Missing HSTS Header",
                    "HTTP response headers",
                    "Strict-Transport-Security header is missing"
            ));
        } else if (maxAge > 0 && maxAge < 15552000) {
            findings.add(createFinding(
                    host + ":" + port,
                    Severity.LOW,
                    "Weak HSTS max-age",
                    "max-age: " + maxAge,
                    "HSTS max-age should be at least 6 months (15552000 seconds)"
            ));
        }

        return findings;
    }

    private List<VulnerabilityFinding> checkCertificateValidity(X509Certificate cert, String host, int port) {
        List<VulnerabilityFinding> findings = new ArrayList<>();

        Instant now = Instant.now();

        try {
            cert.checkValidity(now.toDate());
        } catch (Exception e) {
            findings.add(createFinding(
                    host + ":" + port,
                    Severity.CRITICAL,
                    "Invalid Certificate",
                    "Certificate validity check",
                    "Certificate is not valid: " + e.getMessage()
            ));
        }

        Instant notAfter = cert.getNotAfter().toInstant();
        Instant notBefore = cert.getNotBefore().toInstant();

        if (notAfter.isBefore(now)) {
            findings.add(createFinding(
                    host + ":" + port,
                    Severity.CRITICAL,
                    "Expired Certificate",
                    "Expired: " + notAfter,
                    "Certificate expired on " + notAfter
            ));
        } else {
            long daysUntilExpiry = ChronoUnit.DAYS.between(now, notAfter);

            if (daysUntilExpiry <= 7) {
                findings.add(createFinding(
                        host + ":" + port,
                        Severity.HIGH,
                        "Certificate Expiring Soon",
                        "Expires in " + daysUntilExpiry + " days",
                        "Certificate expires on " + notAfter
                ));
            } else if (daysUntilExpiry <= 30) {
                findings.add(createFinding(
                        host + ":" + port,
                        Severity.MEDIUM,
                        "Certificate Expiring Soon",
                        "Expires in " + daysUntilExpiry + " days",
                        "Certificate expires on " + notAfter
                ));
            }
        }

        if (notBefore.isAfter(now)) {
            findings.add(createFinding(
                    host + ":" + port,
                    Severity.HIGH,
                    "Certificate Not Yet Valid",
                    "Valid from: " + notBefore,
                    "Certificate is not valid until " + notBefore
            ));
        }

        X500Principal issuer = cert.getIssuerX500Principal();
        X500Principal subject = cert.getSubjectX500Principal();

        if (issuer.equals(subject)) {
            findings.add(createFinding(
                    host + ":" + port,
                    Severity.HIGH,
                    "Self-Signed Certificate",
                    "Self-signed",
                    "Certificate is self-signed and not trusted by default"
            ));
        }

        return findings;
    }

    private boolean hostnameMatchesCertificate(String hostname, String certificateName) {
        String certLower = certificateName.toLowerCase();
        String hostLower = hostname.toLowerCase();

        if (certLower.contains("cn=" + hostLower) || certLower.endsWith("cn=" + hostLower)) {
            return true;
        }

        if (certLower.contains("cn=*.")) {
            int wildcardStart = certLower.indexOf("cn=*.");
            String wildcardDomain = certLower.substring(wildcardStart + 5);

            if (wildcardDomain.contains(",")) {
                wildcardDomain = wildcardDomain.substring(0, wildcardDomain.indexOf(","));
            }

            wildcardDomain = "*." + wildcardDomain;

            if (hostLower.startsWith(wildcardDomain.replace("*.", "")) ||
                hostLower.matches(wildcardDomain.replace("*.", "[^.]+"))) {
                return true;
            }
        }

        return certLower.contains(hostLower);
    }

    private boolean containsAny(String text, Set<String> patterns) {
        return patterns.stream().anyMatch(text::contains);
    }

    private VulnerabilityFinding createFinding(String location, Severity severity, String title,
                                               String evidence, String description) {
        return VulnerabilityFinding.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                title,
                severity,
                "TLS",
                location,
                evidence + ": " + description,
                Instant.now()
        );
    }

    private void validateHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host cannot be null or blank");
        }

        String lowerHost = host.toLowerCase();

        for (String pattern : INTERNAL_PATTERNS) {
            if (lowerHost.contains(pattern)) {
                throw new IllegalArgumentException(
                        "SSRF protection: Cannot scan internal addresses (" + pattern + ")"
                );
            }
        }
    }

    private void validatePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
    }
}

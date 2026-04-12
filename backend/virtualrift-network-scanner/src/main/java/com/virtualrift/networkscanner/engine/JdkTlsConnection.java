package com.virtualrift.networkscanner.engine;

import com.virtualrift.networkscanner.config.NetworkScannerProperties;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class JdkTlsConnection implements TlsConnection {

    private final SSLSocketFactory socketFactory;
    private final HttpClient httpClient;
    private final NetworkScannerProperties properties;

    public JdkTlsConnection(NetworkScannerProperties properties) {
        this.properties = properties;
        this.socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectionTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public Optional<X509Certificate> fetchCertificate(String host, int port) {
        return withSession(host, port)
                .flatMap(session -> {
                    try {
                        Certificate[] certificates = session.getPeerCertificates();
                        if (certificates.length > 0 && certificates[0] instanceof X509Certificate certificate) {
                            return Optional.of(certificate);
                        }
                        return Optional.empty();
                    } catch (SSLPeerUnverifiedException e) {
                        return Optional.empty();
                    }
                });
    }

    @Override
    public List<String> getSupportedProtocols(String host, int port) {
        return withSession(host, port)
                .map(SSLSession::getProtocol)
                .filter(protocol -> protocol != null && !protocol.isBlank())
                .map(List::of)
                .orElseGet(List::of);
    }

    @Override
    public List<String> getCipherSuites(String host, int port) {
        return withSession(host, port)
                .map(SSLSession::getCipherSuite)
                .filter(cipher -> cipher != null && !cipher.isBlank())
                .map(List::of)
                .orElseGet(List::of);
    }

    @Override
    public List<String> getKeyExchangeMethods(String host, int port) {
        return getCipherSuites(host, port).stream()
                .map(this::extractKeyExchange)
                .filter(method -> !method.isBlank())
                .distinct()
                .toList();
    }

    @Override
    public List<String> getHttpHeaders(String host, int port) {
        try {
            URI uri = URI.create("https://" + host + ":" + port + "/");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(requestTimeout())
                    .header("User-Agent", properties.getUserAgent())
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.headers().map().entrySet().stream()
                    .flatMap(entry -> entry.getValue().stream()
                            .map(value -> entry.getKey() + ": " + value))
                    .toList();
        } catch (IOException | IllegalArgumentException e) {
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    @Override
    public boolean isSecureConnection(String host, int port) {
        return withSession(host, port)
                .map(SSLSession::getProtocol)
                .map(protocol -> "TLSv1.2".equals(protocol) || "TLSv1.3".equals(protocol))
                .orElse(false);
    }

    private Optional<SSLSession> withSession(String host, int port) {
        try (SSLSocket socket = (SSLSocket) socketFactory.createSocket()) {
            socket.connect(new InetSocketAddress(host, port), (int) connectionTimeout().toMillis());
            socket.setSoTimeout((int) requestTimeout().toMillis());
            socket.startHandshake();
            return Optional.of(socket.getSession());
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String extractKeyExchange(String cipherSuite) {
        String normalized = cipherSuite.toUpperCase(Locale.ROOT);
        List<String> parts = Arrays.asList(normalized.split("_"));

        if (parts.contains("ECDHE")) {
            return "ECDHE";
        }
        if (parts.contains("DHE") || parts.contains("EDH")) {
            return "DHE";
        }
        if (parts.contains("RSA")) {
            return "RSA";
        }
        return "";
    }

    private Duration connectionTimeout() {
        return Duration.ofSeconds(Math.max(1, properties.getConnectionTimeoutSeconds()));
    }

    private Duration requestTimeout() {
        return Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds()));
    }
}

package com.virtualrift.tenant.service;

import com.sun.net.httpserver.HttpServer;
import com.virtualrift.tenant.model.ScanTarget;
import com.virtualrift.tenant.model.TargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HttpScanTargetOwnershipVerifier Tests")
class HttpScanTargetOwnershipVerifierTest {

    @Test
    @DisplayName("should verify URL target when well-known file contains token")
    void verify_quandoArquivoWellKnownContemToken_retornaVerified() throws Exception {
        HttpServer server = startServer("virtualrift-token");
        try {
            ScanTarget target = scanTarget("http://127.0.0.1:" + server.getAddress().getPort(), TargetType.URL);
            target.setVerificationToken("virtualrift-token");
            HttpScanTargetOwnershipVerifier verifier = verifierAllowingLocalTargets();

            ScanTargetOwnershipVerificationResult result = verifier.verify(target);

            assertTrue(result.verified());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("should fail URL target when well-known file misses token")
    void verify_quandoArquivoWellKnownNaoContemToken_retornaFalha() throws Exception {
        HttpServer server = startServer("other-token");
        try {
            ScanTarget target = scanTarget("http://127.0.0.1:" + server.getAddress().getPort(), TargetType.URL);
            target.setVerificationToken("virtualrift-token");
            HttpScanTargetOwnershipVerifier verifier = verifierAllowingLocalTargets();

            ScanTargetOwnershipVerificationResult result = verifier.verify(target);

            assertFalse(result.verified());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("should block private verification targets by default")
    void verify_quandoTargetLocalSemBypass_retornaFalha() {
        ScanTarget target = scanTarget("http://127.0.0.1:8080", TargetType.URL);
        target.setVerificationToken("virtualrift-token");

        ScanTargetOwnershipVerificationResult result = new HttpScanTargetOwnershipVerifier().verify(target);

        assertFalse(result.verified());
    }

    @Test
    @DisplayName("should not support IP range verification yet")
    void verify_quandoIpRange_retornaFalha() {
        ScanTarget target = scanTarget("203.0.113.0/24", TargetType.IP_RANGE);

        ScanTargetOwnershipVerificationResult result = verifierAllowingLocalTargets().verify(target);

        assertFalse(result.verified());
    }

    private HttpServer startServer(String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/virtualrift-verification.txt", exchange -> {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private ScanTarget scanTarget(String target, TargetType targetType) {
        return new ScanTarget(UUID.randomUUID(), UUID.randomUUID(), target, targetType, null);
    }

    private HttpScanTargetOwnershipVerifier verifierAllowingLocalTargets() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        return new HttpScanTargetOwnershipVerifier(httpClient, true);
    }
}

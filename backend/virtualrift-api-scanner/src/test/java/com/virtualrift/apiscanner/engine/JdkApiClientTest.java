package com.virtualrift.apiscanner.engine;

import com.sun.net.httpserver.HttpServer;
import com.virtualrift.apiscanner.config.ApiScannerProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JdkApiClient Tests")
class JdkApiClientTest {

    private HttpServer server;
    private JdkApiClient client;

    @BeforeEach
    void setUp() throws IOException {
        ApiScannerProperties properties = new ApiScannerProperties();
        properties.setRequestTimeoutSeconds(2);
        properties.setUserAgent("VirtualRift-Test");
        client = new JdkApiClient(properties);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("should send method and headers")
    void sendRequest_quandoHeadersInformados_enviaRequisicao() {
        server.createContext("/users", exchange -> {
            String method = exchange.getRequestMethod();
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] response = (method + ":" + auth).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        HttpResponse<String> response = client.sendRequest(
                baseUri().resolve("/users").toString(),
                "GET",
                Map.of("Authorization", "Bearer test-token")
        );

        assertEquals(200, response.statusCode());
        assertEquals("GET:Bearer test-token", response.body());
    }

    @Test
    @DisplayName("should send request body")
    void sendRequestWithBody_quandoBodyInformado_enviaPayload() {
        server.createContext("/users", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        HttpResponse<String> response = client.sendRequestWithBody(
                baseUri().resolve("/users").toString(),
                "POST",
                "{\"role\":\"admin\"}"
        );

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("admin"));
    }

    private URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
}

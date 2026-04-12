package com.virtualrift.webscanner.engine;

import com.sun.net.httpserver.HttpServer;
import com.virtualrift.webscanner.config.WebScannerProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JdkWebScannerHttpClient Tests")
class JdkWebScannerHttpClientTest {

    private HttpServer server;
    private JdkWebScannerHttpClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        WebScannerProperties properties = new WebScannerProperties();
        properties.setRequestTimeoutSeconds(2);
        client = new JdkWebScannerHttpClient(properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("should send encoded query payload")
    void sendRequest_quandoPayloadPossuiCaracteresEspeciais_enviaQueryEncoded() {
        server.createContext("/search", exchange -> {
            byte[] body = exchange.getRequestURI().getRawQuery().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        String response = client.sendRequest(baseUrl + "/search", "q=<script>alert('XSS')</script>").orElseThrow();

        assertTrue(response.contains("q=%3Cscript%3Ealert"));
    }

    @Test
    @DisplayName("should extract inline and external JavaScript")
    void fetchJavaScript_quandoPaginaPossuiScripts_retornaConteudo() {
        server.createContext("/", exchange -> {
            byte[] body = """
                    <html>
                    <script>element.innerHTML = location.hash;</script>
                    <script src="/app.js"></script>
                    </html>
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/app.js", exchange -> {
            byte[] body = "eval(window.name);".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        List<String> scripts = client.fetchJavaScript(baseUrl + "/");

        assertEquals(2, scripts.size());
        assertTrue(scripts.stream().anyMatch(script -> script.contains("location.hash")));
        assertTrue(scripts.stream().anyMatch(script -> script.contains("window.name")));
    }
}

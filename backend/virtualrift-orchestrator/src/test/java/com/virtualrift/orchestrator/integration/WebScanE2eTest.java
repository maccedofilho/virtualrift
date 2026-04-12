package com.virtualrift.orchestrator.integration;

import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.orchestrator.VirtualRiftOrchestratorApplication;
import com.virtualrift.orchestrator.dto.CreateScanRequest;
import com.virtualrift.orchestrator.dto.ScanResultResponse;
import com.virtualrift.orchestrator.service.ScanOrchestratorService;
import com.virtualrift.tenant.client.TenantClient;
import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.TenantQuota;
import com.virtualrift.webscanner.config.WebScannerProperties;
import com.virtualrift.webscanner.engine.HttpClient;
import com.virtualrift.webscanner.engine.SqlInjectionDetector;
import com.virtualrift.webscanner.engine.WebScanEngine;
import com.virtualrift.webscanner.engine.XssDetector;
import com.virtualrift.webscanner.kafka.WebScanEventConsumer;
import com.virtualrift.webscanner.kafka.WebScanEventPublisher;
import com.virtualrift.webscanner.service.WebScanWorkerService;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = {
        VirtualRiftOrchestratorApplication.class,
        WebScanE2eTest.WebWorkerTestConfig.class
})
@DisplayName("WEB scan E2E with Testcontainers")
class WebScanE2eTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("virtualrift")
            .withUsername("virtualrift")
            .withPassword("virtualrift");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @MockBean
    private TenantClient tenantClient;

    private final ScanOrchestratorService scanOrchestratorService;
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    WebScanE2eTest(ScanOrchestratorService scanOrchestratorService,
                   KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry) {
        this.scanOrchestratorService = scanOrchestratorService;
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
    }

    @Test
    @DisplayName("should publish requested event, run WEB worker, publish completed event and persist findings")
    void webScan_quandoAlvoRefletePayload_persisteResultadoCompleto() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantQuota quota = new TenantQuota(tenantId, 100, 10, 100, 300, true);

        when(tenantClient.getQuota(tenantId)).thenReturn(quota);
        when(tenantClient.getPlan(tenantId)).thenReturn(Plan.TRIAL);
        when(tenantClient.isScanTargetAuthorized(
                tenantId,
                "https://example.com/search",
                ScanType.WEB
        )).thenReturn(true);

        waitForKafkaAssignments();

        var response = scanOrchestratorService.createScan(
                new CreateScanRequest("https://example.com/search", ScanType.WEB, 1, 30),
                tenantId,
                userId
        );

        ScanResultResponse result = waitForCompletedResult(response.id(), tenantId);

        assertEquals(ScanStatus.COMPLETED, result.status());
        assertEquals(1, result.totalFindings());
        assertEquals(1, result.highCount());
        assertEquals(15, result.riskScore());
        assertNotNull(result.completedAt());
        assertEquals(response.id(), result.findings().getFirst().scanId());
        assertEquals(tenantId, result.findings().getFirst().tenantId());
        assertEquals("Reflected XSS", result.findings().getFirst().title());
        assertEquals("XSS", result.findings().getFirst().category());
        assertTrue(result.findings().getFirst().location().contains("Parameter: q"));
    }

    private void waitForKafkaAssignments() {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));

        while (Instant.now().isBefore(deadline)) {
            if (!kafkaListenerEndpointRegistry.getListenerContainers().isEmpty()
                    && kafkaListenerEndpointRegistry.getListenerContainers().stream()
                    .allMatch(this::hasAssignedPartitions)) {
                return;
            }

            sleep();
        }

        throw new AssertionError("Kafka listeners were not assigned partitions");
    }

    private boolean hasAssignedPartitions(MessageListenerContainer container) {
        return container.isRunning() && !container.getAssignedPartitions().isEmpty();
    }

    private ScanResultResponse waitForCompletedResult(UUID scanId, UUID tenantId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        AssertionError lastError = null;

        while (Instant.now().isBefore(deadline)) {
            try {
                ScanResultResponse result = scanOrchestratorService.getResult(scanId, tenantId);
                if (result.status() == ScanStatus.COMPLETED && result.totalFindings() > 0) {
                    return result;
                }
            } catch (AssertionError e) {
                lastError = e;
            }

            sleep();
        }

        if (lastError != null) {
            throw lastError;
        }
        return scanOrchestratorService.getResult(scanId, tenantId);
    }

    private void sleep() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for WEB E2E result", e);
        }
    }

    @TestConfiguration
    static class WebWorkerTestConfig {

        @Bean
        WebScannerProperties webScannerProperties() {
            WebScannerProperties properties = new WebScannerProperties();
            properties.setParameterNames(List.of("q"));
            properties.setXssPayloads(List.of("<script>alert('XSS')</script>"));
            properties.setMaxFindings(10);
            properties.setRequestTimeoutSeconds(2);
            properties.setDomXssEnabled(false);
            properties.setSqlErrorEnabled(false);
            properties.setSqlBooleanEnabled(false);
            properties.setSqlUnionEnabled(false);
            return properties;
        }

        @Bean
        HttpClient fakeWebScannerHttpClient() {
            return new FakeHttpClient();
        }

        @Bean
        XssDetector xssDetector(HttpClient fakeWebScannerHttpClient) {
            return new XssDetector(fakeWebScannerHttpClient);
        }

        @Bean
        SqlInjectionDetector sqlInjectionDetector(HttpClient fakeWebScannerHttpClient) {
            return new SqlInjectionDetector(fakeWebScannerHttpClient);
        }

        @Bean
        WebScanEngine webScanEngine(XssDetector xssDetector,
                                    SqlInjectionDetector sqlInjectionDetector,
                                    WebScannerProperties webScannerProperties) {
            return new WebScanEngine(xssDetector, sqlInjectionDetector, webScannerProperties);
        }

        @Bean
        ProducerFactory<String, ScanCompletedEvent> webScanCompletedProducerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            return new DefaultKafkaProducerFactory<>(producerConfig(bootstrapServers));
        }

        @Bean
        KafkaTemplate<String, ScanCompletedEvent> webScanCompletedKafkaTemplate(
                ProducerFactory<String, ScanCompletedEvent> webScanCompletedProducerFactory) {
            return new KafkaTemplate<>(webScanCompletedProducerFactory);
        }

        @Bean
        ProducerFactory<String, ScanFailedEvent> webScanFailedProducerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            return new DefaultKafkaProducerFactory<>(producerConfig(bootstrapServers));
        }

        @Bean
        KafkaTemplate<String, ScanFailedEvent> webScanFailedKafkaTemplate(
                ProducerFactory<String, ScanFailedEvent> webScanFailedProducerFactory) {
            return new KafkaTemplate<>(webScanFailedProducerFactory);
        }

        @Bean
        ConsumerFactory<String, ScanRequestedEvent> scanRequestedConsumerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            Map<String, Object> config = new HashMap<>();
            config.put("bootstrap.servers", bootstrapServers);
            config.put("group.id", "virtualrift-web-e2e");
            config.put("key.deserializer", StringDeserializer.class);
            config.put("value.deserializer", JsonDeserializer.class);
            config.put("auto.offset.reset", "earliest");
            config.put(JsonDeserializer.TRUSTED_PACKAGES, ScanRequestedEvent.class.getPackageName());
            return new DefaultKafkaConsumerFactory<>(
                    config,
                    new StringDeserializer(),
                    new JsonDeserializer<>(ScanRequestedEvent.class)
            );
        }

        @Bean
        ConcurrentKafkaListenerContainerFactory<String, ScanRequestedEvent> scanRequestedKafkaListenerContainerFactory(
                ConsumerFactory<String, ScanRequestedEvent> scanRequestedConsumerFactory) {
            ConcurrentKafkaListenerContainerFactory<String, ScanRequestedEvent> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(scanRequestedConsumerFactory);
            return factory;
        }

        @Bean
        WebScanEventPublisher webScanEventPublisher(
                KafkaTemplate<String, ScanCompletedEvent> webScanCompletedKafkaTemplate,
                KafkaTemplate<String, ScanFailedEvent> webScanFailedKafkaTemplate) {
            return new WebScanEventPublisher(webScanCompletedKafkaTemplate, webScanFailedKafkaTemplate);
        }

        @Bean
        WebScanWorkerService webScanWorkerService(WebScanEngine webScanEngine,
                                                  WebScanEventPublisher webScanEventPublisher) {
            return new WebScanWorkerService(webScanEngine, webScanEventPublisher);
        }

        @Bean
        WebScanEventConsumer webScanEventConsumer(WebScanWorkerService webScanWorkerService) {
            return new WebScanEventConsumer(webScanWorkerService);
        }

        private static Map<String, Object> producerConfig(String bootstrapServers) {
            Map<String, Object> config = new HashMap<>();
            config.put("bootstrap.servers", bootstrapServers);
            config.put("key.serializer", StringSerializer.class);
            config.put("value.serializer", JsonSerializer.class);
            return config;
        }
    }

    static class FakeHttpClient implements HttpClient {

        @Override
        public Optional<String> sendRequest(String url, String payload) {
            if (payload != null && payload.contains("<script>alert('XSS')</script>")) {
                return Optional.of("<html><body>" + payload + "</body></html>");
            }
            return Optional.of("<html><body>clean</body></html>");
        }

        @Override
        public Optional<String> getPage(String url) {
            return Optional.of("<html><body>clean</body></html>");
        }

        @Override
        public Optional<String> sendRequestWithCookie(String url, String payload, String cookieName, String cookieValue) {
            return sendRequest(url, payload);
        }

        @Override
        public Optional<String> sendRequestWithCookie(String url, String payload) {
            return sendRequest(url, payload);
        }

        @Override
        public Optional<String> sendRequestWithHeader(String url, String payload, String headerName, String headerValue) {
            return sendRequest(url, payload);
        }

        @Override
        public Optional<String> sendRequestWithHeader(String url, String payload) {
            return sendRequest(url, payload);
        }

        @Override
        public Optional<String> sendJson(String url, String jsonPayload) {
            return Optional.empty();
        }

        @Override
        public List<String> fetchJavaScript(String url) {
            return List.of();
        }

        @Override
        public long measureResponseTime(String url) {
            return 0L;
        }
    }
}

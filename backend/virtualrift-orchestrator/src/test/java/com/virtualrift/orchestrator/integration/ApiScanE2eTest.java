package com.virtualrift.orchestrator.integration;

import com.virtualrift.apiscanner.config.ApiScannerProperties;
import com.virtualrift.apiscanner.engine.ApiClient;
import com.virtualrift.apiscanner.engine.ApiScanEngine;
import com.virtualrift.apiscanner.engine.ApiVulnerabilityDetector;
import com.virtualrift.apiscanner.kafka.ApiScanEventConsumer;
import com.virtualrift.apiscanner.kafka.ApiScanEventPublisher;
import com.virtualrift.apiscanner.service.ApiScanWorkerService;
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

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
        ApiScanE2eTest.ApiWorkerTestConfig.class
})
@DisplayName("API scan E2E with Testcontainers")
class ApiScanE2eTest {

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
    ApiScanE2eTest(ScanOrchestratorService scanOrchestratorService,
                   KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry) {
        this.scanOrchestratorService = scanOrchestratorService;
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
    }

    @Test
    @DisplayName("should publish requested event, run API worker, publish completed event and persist findings")
    void apiScan_quandoRespostaExpoeCampoSensivel_persisteResultadoCompleto() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantQuota quota = new TenantQuota(tenantId, 100, 10, 100, 300, true);

        when(tenantClient.getQuota(tenantId)).thenReturn(quota);
        when(tenantClient.getPlan(tenantId)).thenReturn(Plan.STARTER);

        waitForKafkaAssignments();

        var response = scanOrchestratorService.createScan(
                new CreateScanRequest("https://api.example.com/users", ScanType.API, 1, 30),
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
        assertEquals("Excessive Data Exposure - PASSWORD", result.findings().getFirst().title());
        assertEquals("API_SECURITY", result.findings().getFirst().category());
        assertTrue(result.findings().getFirst().evidence().contains("Field detected in response"));
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
            throw new AssertionError("Interrupted while waiting for API E2E result", e);
        }
    }

    @TestConfiguration
    static class ApiWorkerTestConfig {

        @Bean
        ApiScannerProperties apiScannerProperties() {
            ApiScannerProperties properties = new ApiScannerProperties();
            properties.setMaxFindings(10);
            properties.setRequestTimeoutSeconds(2);
            properties.setDefaultMethod("GET");
            properties.setEndpointScanEnabled(true);
            properties.setCorsScanEnabled(false);
            properties.setRateLimitScanEnabled(false);
            properties.setOpenApiScanEnabled(false);
            properties.setInjectionScanEnabled(false);
            properties.setJwtScanEnabled(false);
            return properties;
        }

        @Bean
        ApiClient fakeApiScannerClient() {
            return new FakeApiClient();
        }

        @Bean
        ApiVulnerabilityDetector apiVulnerabilityDetector(ApiClient fakeApiScannerClient) {
            return new ApiVulnerabilityDetector(fakeApiScannerClient);
        }

        @Bean
        ApiScanEngine apiScanEngine(ApiVulnerabilityDetector apiVulnerabilityDetector,
                                    ApiScannerProperties apiScannerProperties) {
            return new ApiScanEngine(apiVulnerabilityDetector, apiScannerProperties);
        }

        @Bean
        ProducerFactory<String, ScanCompletedEvent> apiScanCompletedProducerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            return new DefaultKafkaProducerFactory<>(producerConfig(bootstrapServers));
        }

        @Bean
        KafkaTemplate<String, ScanCompletedEvent> apiScanCompletedKafkaTemplate(
                ProducerFactory<String, ScanCompletedEvent> apiScanCompletedProducerFactory) {
            return new KafkaTemplate<>(apiScanCompletedProducerFactory);
        }

        @Bean
        ProducerFactory<String, ScanFailedEvent> apiScanFailedProducerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            return new DefaultKafkaProducerFactory<>(producerConfig(bootstrapServers));
        }

        @Bean
        KafkaTemplate<String, ScanFailedEvent> apiScanFailedKafkaTemplate(
                ProducerFactory<String, ScanFailedEvent> apiScanFailedProducerFactory) {
            return new KafkaTemplate<>(apiScanFailedProducerFactory);
        }

        @Bean
        ConsumerFactory<String, ScanRequestedEvent> scanRequestedConsumerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            Map<String, Object> config = new HashMap<>();
            config.put("bootstrap.servers", bootstrapServers);
            config.put("group.id", "virtualrift-api-e2e");
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
        ApiScanEventPublisher apiScanEventPublisher(
                KafkaTemplate<String, ScanCompletedEvent> apiScanCompletedKafkaTemplate,
                KafkaTemplate<String, ScanFailedEvent> apiScanFailedKafkaTemplate) {
            return new ApiScanEventPublisher(apiScanCompletedKafkaTemplate, apiScanFailedKafkaTemplate);
        }

        @Bean
        ApiScanWorkerService apiScanWorkerService(ApiScanEngine apiScanEngine,
                                                  ApiScanEventPublisher apiScanEventPublisher) {
            return new ApiScanWorkerService(apiScanEngine, apiScanEventPublisher);
        }

        @Bean
        ApiScanEventConsumer apiScanEventConsumer(ApiScanWorkerService apiScanWorkerService) {
            return new ApiScanEventConsumer(apiScanWorkerService);
        }

        private static Map<String, Object> producerConfig(String bootstrapServers) {
            Map<String, Object> config = new HashMap<>();
            config.put("bootstrap.servers", bootstrapServers);
            config.put("key.serializer", StringSerializer.class);
            config.put("value.serializer", JsonSerializer.class);
            return config;
        }
    }

    static class FakeApiClient implements ApiClient {

        @Override
        public HttpResponse<String> sendRequest(String url, String method, Map<String, String> headers) {
            return response(url, "{\"user\":\"john\",\"password\":\"secret123\"}");
        }

        @Override
        public HttpResponse<String> sendRequest(String url, String method) {
            return response(url, "{\"user\":\"john\",\"password\":\"secret123\"}");
        }

        @Override
        public HttpResponse<String> sendRequestWithBody(String url, String method, String body) {
            return response(url, "{\"ok\":true}");
        }

        @Override
        public HttpResponse<String> sendRequestWithBody(String url, String method, String body, Map<String, String> headers) {
            return response(url, "{\"ok\":true}");
        }

        @Override
        public HttpResponse<String> sendRequestWithAuth(String url, String method, String token) {
            return response(url, "{\"ok\":true}");
        }

        private HttpResponse<String> response(String url, String body) {
            return new FakeResponse(200, body, URI.create(url));
        }
    }

    private record FakeResponse(int statusCode, String body, URI uri) implements HttpResponse<String> {

        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(uri).build();
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}

package com.virtualrift.orchestrator.integration;

import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.networkscanner.config.NetworkScannerProperties;
import com.virtualrift.networkscanner.engine.NetworkScanEngine;
import com.virtualrift.networkscanner.engine.TlsAnalyzer;
import com.virtualrift.networkscanner.engine.TlsConnection;
import com.virtualrift.networkscanner.kafka.NetworkScanEventConsumer;
import com.virtualrift.networkscanner.kafka.NetworkScanEventPublisher;
import com.virtualrift.networkscanner.service.NetworkScanWorkerService;
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

import java.security.cert.X509Certificate;
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
        NetworkScanE2eTest.NetworkWorkerTestConfig.class
})
@DisplayName("Network scan E2E with Testcontainers")
class NetworkScanE2eTest {

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
    NetworkScanE2eTest(ScanOrchestratorService scanOrchestratorService,
                       KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry) {
        this.scanOrchestratorService = scanOrchestratorService;
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
    }

    @Test
    @DisplayName("should publish requested event, run network worker, publish completed event and persist findings")
    void networkScan_quandoTlsFraco_persisteResultadoCompleto() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantQuota quota = new TenantQuota(tenantId, 100, 10, 100, 300, true);

        when(tenantClient.getQuota(tenantId)).thenReturn(quota);
        when(tenantClient.getPlan(tenantId)).thenReturn(Plan.PROFESSIONAL);
        when(tenantClient.isScanTargetAuthorized(
                tenantId,
                "example.com:443",
                ScanType.NETWORK
        )).thenReturn(true);

        waitForKafkaAssignments();

        var response = scanOrchestratorService.createScan(
                new CreateScanRequest("example.com:443", ScanType.NETWORK, 1, 30),
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
        assertEquals("Weak TLS Protocol", result.findings().getFirst().title());
        assertEquals("TLS", result.findings().getFirst().category());
        assertEquals("example.com:443", result.findings().getFirst().location());
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
            throw new AssertionError("Interrupted while waiting for Network E2E result", e);
        }
    }

    @TestConfiguration
    static class NetworkWorkerTestConfig {

        @Bean
        NetworkScannerProperties networkScannerProperties() {
            NetworkScannerProperties properties = new NetworkScannerProperties();
            properties.setMaxFindings(10);
            properties.setRequestTimeoutSeconds(2);
            properties.setConnectionTimeoutSeconds(2);
            properties.setDefaultPort(443);
            properties.setProtocolScanEnabled(true);
            properties.setCertificateScanEnabled(false);
            properties.setCipherScanEnabled(false);
            properties.setKeyExchangeScanEnabled(false);
            properties.setHostnameScanEnabled(false);
            properties.setHstsScanEnabled(false);
            return properties;
        }

        @Bean
        TlsConnection fakeNetworkTlsConnection() {
            return new FakeTlsConnection();
        }

        @Bean
        TlsAnalyzer networkTlsAnalyzer(TlsConnection fakeNetworkTlsConnection) {
            return new TlsAnalyzer(fakeNetworkTlsConnection);
        }

        @Bean
        NetworkScanEngine networkScanEngine(TlsAnalyzer networkTlsAnalyzer,
                                            NetworkScannerProperties networkScannerProperties) {
            return new NetworkScanEngine(networkTlsAnalyzer, networkScannerProperties);
        }

        @Bean
        ProducerFactory<String, ScanCompletedEvent> networkScanCompletedProducerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            return new DefaultKafkaProducerFactory<>(producerConfig(bootstrapServers));
        }

        @Bean
        KafkaTemplate<String, ScanCompletedEvent> networkScanCompletedKafkaTemplate(
                ProducerFactory<String, ScanCompletedEvent> networkScanCompletedProducerFactory) {
            return new KafkaTemplate<>(networkScanCompletedProducerFactory);
        }

        @Bean
        ProducerFactory<String, ScanFailedEvent> networkScanFailedProducerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            return new DefaultKafkaProducerFactory<>(producerConfig(bootstrapServers));
        }

        @Bean
        KafkaTemplate<String, ScanFailedEvent> networkScanFailedKafkaTemplate(
                ProducerFactory<String, ScanFailedEvent> networkScanFailedProducerFactory) {
            return new KafkaTemplate<>(networkScanFailedProducerFactory);
        }

        @Bean
        ConsumerFactory<String, ScanRequestedEvent> scanRequestedConsumerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            Map<String, Object> config = new HashMap<>();
            config.put("bootstrap.servers", bootstrapServers);
            config.put("group.id", "virtualrift-network-e2e");
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
        NetworkScanEventPublisher networkScanEventPublisher(
                KafkaTemplate<String, ScanCompletedEvent> networkScanCompletedKafkaTemplate,
                KafkaTemplate<String, ScanFailedEvent> networkScanFailedKafkaTemplate) {
            return new NetworkScanEventPublisher(networkScanCompletedKafkaTemplate, networkScanFailedKafkaTemplate);
        }

        @Bean
        NetworkScanWorkerService networkScanWorkerService(NetworkScanEngine networkScanEngine,
                                                          NetworkScanEventPublisher networkScanEventPublisher) {
            return new NetworkScanWorkerService(networkScanEngine, networkScanEventPublisher);
        }

        @Bean
        NetworkScanEventConsumer networkScanEventConsumer(NetworkScanWorkerService networkScanWorkerService) {
            return new NetworkScanEventConsumer(networkScanWorkerService);
        }

        private static Map<String, Object> producerConfig(String bootstrapServers) {
            Map<String, Object> config = new HashMap<>();
            config.put("bootstrap.servers", bootstrapServers);
            config.put("key.serializer", StringSerializer.class);
            config.put("value.serializer", JsonSerializer.class);
            return config;
        }
    }

    static class FakeTlsConnection implements TlsConnection {

        @Override
        public Optional<X509Certificate> fetchCertificate(String host, int port) {
            return Optional.empty();
        }

        @Override
        public List<String> getSupportedProtocols(String host, int port) {
            return List.of("TLSv1");
        }

        @Override
        public List<String> getCipherSuites(String host, int port) {
            return List.of();
        }

        @Override
        public List<String> getKeyExchangeMethods(String host, int port) {
            return List.of();
        }

        @Override
        public List<String> getHttpHeaders(String host, int port) {
            return List.of();
        }

        @Override
        public boolean isSecureConnection(String host, int port) {
            return false;
        }
    }
}

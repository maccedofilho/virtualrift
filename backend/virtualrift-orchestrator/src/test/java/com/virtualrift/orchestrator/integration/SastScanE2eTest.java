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
import com.virtualrift.sast.config.SastProperties;
import com.virtualrift.sast.engine.SastAnalyzer;
import com.virtualrift.sast.kafka.SastScanEventConsumer;
import com.virtualrift.sast.kafka.SastScanEventPublisher;
import com.virtualrift.sast.service.GitClient;
import com.virtualrift.sast.service.SastScanWorkerService;
import com.virtualrift.sast.service.SastTargetResolver;
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
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = {
        VirtualRiftOrchestratorApplication.class,
        SastScanE2eTest.SastWorkerTestConfig.class
})
@DisplayName("SAST scan E2E with Testcontainers")
class SastScanE2eTest {

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
    private final FakeGitClient fakeGitClient;

    @Autowired
    SastScanE2eTest(ScanOrchestratorService scanOrchestratorService, FakeGitClient fakeGitClient) {
        this.scanOrchestratorService = scanOrchestratorService;
        this.fakeGitClient = fakeGitClient;
    }

    @Test
    @DisplayName("should publish requested event, run SAST worker, publish completed event and persist findings")
    void sastScan_quandoRepositorioTemFinding_persisteResultadoCompleto() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantQuota quota = new TenantQuota(tenantId, 100, 10, 100, 300, true);

        when(tenantClient.getQuota(tenantId)).thenReturn(quota);
        when(tenantClient.getPlan(tenantId)).thenReturn(Plan.ENTERPRISE);

        var response = scanOrchestratorService.createScan(
                new CreateScanRequest("https://github.com/acme/vulnerable.git", ScanType.SAST, 1, 30),
                tenantId,
                userId
        );

        ScanResultResponse result = waitForCompletedResult(response.id(), tenantId);

        assertEquals(ScanStatus.COMPLETED, result.status());
        assertEquals(1, result.totalFindings());
        assertEquals(1, result.criticalCount());
        assertEquals(50, result.riskScore());
        assertNotNull(result.completedAt());
        assertEquals(response.id(), result.findings().get(0).scanId());
        assertEquals(tenantId, result.findings().get(0).tenantId());
        assertEquals("Hardcoded Password", result.findings().get(0).title());
        assertTrue(result.findings().get(0).evidence().contains("password=****"));
        assertFalse(result.findings().get(0).evidence().contains("SuperSecret123"));
        assertEquals(URI.create("https://github.com/acme/vulnerable.git"), fakeGitClient.lastRepositoryUri);
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
            throw new AssertionError("Interrupted while waiting for SAST E2E result", e);
        }
    }

    @TestConfiguration
    static class SastWorkerTestConfig {

        @Bean
        SastAnalyzer sastAnalyzer() {
            return new SastAnalyzer();
        }

        @Bean
        SastProperties sastProperties() throws IOException {
            SastProperties properties = new SastProperties();
            Path workspaceRoot = Files.createTempDirectory("virtualrift-sast-e2e");
            properties.setWorkspaceRoot(workspaceRoot);
            properties.setCloneTimeoutSeconds(30);
            properties.setMaxRepositoryBytes(10_000_000L);
            properties.setMaxRepositoryFiles(1_000);
            properties.setLocalTargetsEnabled(false);
            return properties;
        }

        @Bean
        FakeGitClient fakeGitClient() {
            return new FakeGitClient();
        }

        @Bean
        SastTargetResolver sastTargetResolver(SastProperties properties, GitClient gitClient) {
            return new SastTargetResolver(properties, gitClient);
        }

        @Bean
        ProducerFactory<String, ScanCompletedEvent> sastScanCompletedProducerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            return new DefaultKafkaProducerFactory<>(producerConfig(bootstrapServers));
        }

        @Bean
        KafkaTemplate<String, ScanCompletedEvent> sastScanCompletedKafkaTemplate(
                ProducerFactory<String, ScanCompletedEvent> sastScanCompletedProducerFactory) {
            return new KafkaTemplate<>(sastScanCompletedProducerFactory);
        }

        @Bean
        ProducerFactory<String, ScanFailedEvent> sastScanFailedProducerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            return new DefaultKafkaProducerFactory<>(producerConfig(bootstrapServers));
        }

        @Bean
        KafkaTemplate<String, ScanFailedEvent> sastScanFailedKafkaTemplate(
                ProducerFactory<String, ScanFailedEvent> sastScanFailedProducerFactory) {
            return new KafkaTemplate<>(sastScanFailedProducerFactory);
        }

        @Bean
        ConsumerFactory<String, ScanRequestedEvent> scanRequestedConsumerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            Map<String, Object> config = new HashMap<>();
            config.put("bootstrap.servers", bootstrapServers);
            config.put("group.id", "virtualrift-sast-e2e");
            config.put("key.deserializer", StringDeserializer.class);
            config.put("value.deserializer", JsonDeserializer.class);
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
        SastScanEventPublisher sastScanEventPublisher(
                KafkaTemplate<String, ScanCompletedEvent> sastScanCompletedKafkaTemplate,
                KafkaTemplate<String, ScanFailedEvent> sastScanFailedKafkaTemplate) {
            return new SastScanEventPublisher(sastScanCompletedKafkaTemplate, sastScanFailedKafkaTemplate);
        }

        @Bean
        SastScanWorkerService sastScanWorkerService(SastAnalyzer analyzer,
                                                    SastTargetResolver targetResolver,
                                                    SastScanEventPublisher publisher) {
            return new SastScanWorkerService(analyzer, targetResolver, publisher);
        }

        @Bean
        SastScanEventConsumer sastScanEventConsumer(SastScanWorkerService workerService) {
            return new SastScanEventConsumer(workerService);
        }

        private static Map<String, Object> producerConfig(String bootstrapServers) {
            Map<String, Object> config = new HashMap<>();
            config.put("bootstrap.servers", bootstrapServers);
            config.put("key.serializer", StringSerializer.class);
            config.put("value.serializer", JsonSerializer.class);
            return config;
        }
    }

    static class FakeGitClient implements GitClient {

        private URI lastRepositoryUri;

        @Override
        public void cloneRepository(URI repositoryUri, Path destination, Duration timeout) {
            lastRepositoryUri = repositoryUri;
            try {
                Files.createDirectories(destination);
                Files.writeString(destination.resolve("RepoExample.java"), """
                        class RepoExample {
                            void run() {
                                String password = "SuperSecret123";
                            }
                        }
                        """);
            } catch (IOException e) {
                throw new IllegalArgumentException("test clone failed", e);
            }
        }
    }
}

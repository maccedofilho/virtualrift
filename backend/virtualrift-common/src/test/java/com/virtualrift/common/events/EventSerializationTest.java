package com.virtualrift.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.common.model.VulnerabilityFinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Event Serialization Tests")
class EventSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Nested
    @DisplayName("ScanRequestedEvent")
    class ScanRequestedEventTest {

        @Test
        @DisplayName("should serialize to JSON")
        void serialize_quandoEvento_serializaParaJson() throws JsonProcessingException {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();

            ScanRequestedEvent event = new ScanRequestedEvent(
                    scanId,
                    tenantId,
                    "https://example.com",
                    "WEB",
                    3,
                    300,
                    Instant.parse("2024-01-15T10:30:00Z")
            );

            String json = objectMapper.writeValueAsString(event);

            assertNotNull(json);
            assertTrue(json.contains("\"scanId\":\"" + scanId + "\""));
            assertTrue(json.contains("\"tenantId\":\"" + tenantId.value() + "\""));
            assertTrue(json.contains("\"target\":\"https://example.com\""));
            assertTrue(json.contains("\"scanType\":\"WEB\""));
            assertTrue(json.contains("\"depth\":3"));
            assertTrue(json.contains("\"timeout\":300"));
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void deserialize_quandoJson_deserializaParaEvento() throws JsonProcessingException {
            String json = """
                    {
                        "scanId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                        "tenantId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
                        "target": "https://example.com",
                        "scanType": "WEB",
                        "depth": 3,
                        "timeout": 300,
                        "requestedAt": "2024-01-15T10:30:00Z"
                    }
                    """;

            ScanRequestedEvent event = objectMapper.readValue(json, ScanRequestedEvent.class);

            assertNotNull(event);
            assertEquals(UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"), event.scanId());
            assertEquals("https://example.com", event.target());
            assertEquals("WEB", event.scanType());
            assertEquals(3, event.depth());
            assertEquals(300, event.timeout());
        }

        @Test
        @DisplayName("should preserve all fields")
        void roundTrip_quandoTodosCampos_preservaDados() throws JsonProcessingException {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();
            Instant requestedAt = Instant.parse("2024-01-15T10:30:00Z");

            ScanRequestedEvent original = new ScanRequestedEvent(
                    scanId,
                    tenantId,
                    "https://api.example.com/v1",
                    "API",
                    5,
                    600,
                    requestedAt
            );

            String json = objectMapper.writeValueAsString(original);
            ScanRequestedEvent deserialized = objectMapper.readValue(json, ScanRequestedEvent.class);

            assertEquals(original.scanId(), deserialized.scanId());
            assertEquals(original.tenantId(), deserialized.tenantId());
            assertEquals(original.target(), deserialized.target());
            assertEquals(original.scanType(), deserialized.scanType());
            assertEquals(original.depth(), deserialized.depth());
            assertEquals(original.timeout(), deserialized.timeout());
            assertEquals(original.requestedAt(), deserialized.requestedAt());
        }

        @Test
        @DisplayName("should handle null optional fields")
        void serialize_quandoCamposOpcionaisNulos_serializaCorretamente() throws JsonProcessingException {
            ScanRequestedEvent event = new ScanRequestedEvent(
                    UUID.randomUUID(),
                    TenantId.generate(),
                    "https://example.com",
                    "WEB",
                    null,
                    null,
                    Instant.now()
            );

            String json = objectMapper.writeValueAsString(event);
            ScanRequestedEvent deserialized = objectMapper.readValue(json, ScanRequestedEvent.class);

            assertNull(deserialized.depth());
            assertNull(deserialized.timeout());
        }
    }

    @Nested
    @DisplayName("ScanCompletedEvent")
    class ScanCompletedEventTest {

        @Test
        @DisplayName("should serialize to JSON")
        void serialize_quandoEvento_serializaParaJson() throws JsonProcessingException {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();

            ScanCompletedEvent event = new ScanCompletedEvent(
                    scanId,
                    tenantId,
                    List.of(),
                    0,
                    0,
                    Instant.parse("2024-01-15T10:30:00Z"),
                    Instant.parse("2024-01-15T10:31:00Z")
            );

            String json = objectMapper.writeValueAsString(event);

            assertNotNull(json);
            assertTrue(json.contains("\"scanId\":\"" + scanId + "\""));
            assertTrue(json.contains("\"tenantId\":\"" + tenantId.value() + "\""));
            assertTrue(json.contains("\"totalFindings\":0"));
            assertTrue(json.contains("\"riskScore\":0"));
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void deserialize_quandoJson_deserializaParaEvento() throws JsonProcessingException {
            String json = """
                    {
                        "scanId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                        "tenantId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
                        "findings": [],
                        "totalFindings": 0,
                        "riskScore": 0,
                        "startedAt": "2024-01-15T10:30:00Z",
                        "completedAt": "2024-01-15T10:31:00Z"
                    }
                    """;

            ScanCompletedEvent event = objectMapper.readValue(json, ScanCompletedEvent.class);

            assertNotNull(event);
            assertEquals(UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"), event.scanId());
            assertEquals(0, event.totalFindings());
            assertEquals(0, event.riskScore());
            assertTrue(event.findings().isEmpty());
        }

        @Test
        @DisplayName("should serialize findings list")
        void serialize_quandoComFindings_incluiLista() throws JsonProcessingException {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();
            UUID findingId = UUID.randomUUID();

            VulnerabilityFinding finding = VulnerabilityFinding.of(
                    findingId, scanId, tenantId,
                    "XSS Vulnerability", Severity.HIGH,
                    "Injection", "/login",
                    "password field reflects input", Instant.now()
            );

            ScanCompletedEvent event = new ScanCompletedEvent(
                    scanId,
                    tenantId,
                    List.of(finding),
                    1,
                    75,
                    Instant.now(),
                    Instant.now()
            );

            String json = objectMapper.writeValueAsString(event);

            assertTrue(json.contains("\"findings\""));
            assertTrue(json.contains("\"title\":\"XSS Vulnerability\""));
            assertTrue(json.contains("\"severity\":\"HIGH\""));
        }

        @Test
        @DisplayName("should preserve all fields with findings")
        void roundTrip_quandoComFindings_preservaDados() throws JsonProcessingException {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();
            UUID findingId = UUID.randomUUID();

            VulnerabilityFinding finding = VulnerabilityFinding.of(
                    findingId, scanId, tenantId,
                    "SQL Injection", Severity.CRITICAL,
                    "Injection", "/api/users",
                    "id parameter vulnerable", Instant.now()
            );

            ScanCompletedEvent original = new ScanCompletedEvent(
                    scanId,
                    tenantId,
                    List.of(finding),
                    1,
                    100,
                    Instant.parse("2024-01-15T10:30:00Z"),
                    Instant.parse("2024-01-15T10:31:30Z")
            );

            String json = objectMapper.writeValueAsString(original);
            ScanCompletedEvent deserialized = objectMapper.readValue(json, ScanCompletedEvent.class);

            assertEquals(original.scanId(), deserialized.scanId());
            assertEquals(original.tenantId(), deserialized.tenantId());
            assertEquals(1, deserialized.findings().size());
            assertEquals("SQL Injection", deserialized.findings().getFirst().title());
            assertEquals(Severity.CRITICAL, deserialized.findings().getFirst().severity());
            assertEquals(1, deserialized.totalFindings());
            assertEquals(100, deserialized.riskScore());
        }
    }

    @Nested
    @DisplayName("ScanFailedEvent")
    class ScanFailedEventTest {

        @Test
        @DisplayName("should serialize to JSON")
        void serialize_quandoEvento_serializaParaJson() throws JsonProcessingException {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();

            ScanFailedEvent event = new ScanFailedEvent(
                    scanId,
                    tenantId,
                    "Connection timeout after 30 seconds",
                    "CONNECTION_TIMEOUT",
                    Instant.parse("2024-01-15T10:30:00Z")
            );

            String json = objectMapper.writeValueAsString(event);

            assertNotNull(json);
            assertTrue(json.contains("\"scanId\":\"" + scanId + "\""));
            assertTrue(json.contains("\"tenantId\":\"" + tenantId.value() + "\""));
            assertTrue(json.contains("\"errorMessage\":\"Connection timeout after 30 seconds\""));
            assertTrue(json.contains("\"errorCode\":\"CONNECTION_TIMEOUT\""));
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void deserialize_quandoJson_deserializaParaEvento() throws JsonProcessingException {
            String json = """
                    {
                        "scanId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                        "tenantId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
                        "errorMessage": "Target unreachable",
                        "errorCode": "TARGET_UNREACHABLE",
                        "failedAt": "2024-01-15T10:30:00Z"
                    }
                    """;

            ScanFailedEvent event = objectMapper.readValue(json, ScanFailedEvent.class);

            assertNotNull(event);
            assertEquals(UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"), event.scanId());
            assertEquals("Target unreachable", event.errorMessage());
            assertEquals("TARGET_UNREACHABLE", event.errorCode());
        }

        @Test
        @DisplayName("should include failure reason")
        void serialize_quandoComMotivo_incluiRazao() throws JsonProcessingException {
            ScanFailedEvent event = new ScanFailedEvent(
                    UUID.randomUUID(),
                    TenantId.generate(),
                    "SSL certificate expired",
                    "SSL_ERROR",
                    Instant.now()
            );

            String json = objectMapper.writeValueAsString(event);
            ScanFailedEvent deserialized = objectMapper.readValue(json, ScanFailedEvent.class);

            assertEquals("SSL certificate expired", deserialized.errorMessage());
            assertEquals("SSL_ERROR", deserialized.errorCode());
        }

        @Test
        @DisplayName("should preserve all fields")
        void roundTrip_quandoTodosCampos_preservaDados() throws JsonProcessingException {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();
            Instant failedAt = Instant.parse("2024-01-15T10:30:45Z");

            ScanFailedEvent original = new ScanFailedEvent(
                    scanId,
                    tenantId,
                    "Maximum scan duration exceeded",
                    "TIMEOUT",
                    failedAt
            );

            String json = objectMapper.writeValueAsString(original);
            ScanFailedEvent deserialized = objectMapper.readValue(json, ScanFailedEvent.class);

            assertEquals(original.scanId(), deserialized.scanId());
            assertEquals(original.tenantId(), deserialized.tenantId());
            assertEquals(original.errorMessage(), deserialized.errorMessage());
            assertEquals(original.errorCode(), deserialized.errorCode());
            assertEquals(original.failedAt(), deserialized.failedAt());
        }
    }

    @Nested
    @DisplayName("ReportGeneratedEvent")
    class ReportGeneratedEventTest {

        @Test
        @DisplayName("should serialize to JSON")
        void serialize_quandoEvento_serializaParaJson() throws JsonProcessingException {
            UUID scanId = UUID.randomUUID();
            UUID reportId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();

            ReportGeneratedEvent event = new ReportGeneratedEvent(
                    reportId,
                    tenantId,
                    scanId,
                    "PDF",
                    "https://storage.example.com/reports/scan-123.pdf",
                    Instant.parse("2024-01-15T10:30:00Z")
            );

            String json = objectMapper.writeValueAsString(event);

            assertNotNull(json);
            assertTrue(json.contains("\"reportId\":\"" + reportId + "\""));
            assertTrue(json.contains("\"tenantId\":\"" + tenantId.value() + "\""));
            assertTrue(json.contains("\"scanId\":\"" + scanId + "\""));
            assertTrue(json.contains("\"format\":\"PDF\""));
            assertTrue(json.contains("\"storageUrl\":\"https://storage.example.com/reports/scan-123.pdf\""));
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void deserialize_quandoJson_deserializaParaEvento() throws JsonProcessingException {
            String json = """
                    {
                        "reportId": "5fa85f64-5717-4562-b3fc-2c963f66afa6",
                        "tenantId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
                        "scanId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                        "format": "PDF",
                        "storageUrl": "https://storage.example.com/reports/scan-123.pdf",
                        "generatedAt": "2024-01-15T10:30:00Z"
                    }
                    """;

            ReportGeneratedEvent event = objectMapper.readValue(json, ReportGeneratedEvent.class);

            assertNotNull(event);
            assertEquals(UUID.fromString("5fa85f64-5717-4562-b3fc-2c963f66afa6"), event.reportId());
            assertEquals(UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"), event.scanId());
            assertEquals("PDF", event.format());
            assertEquals("https://storage.example.com/reports/scan-123.pdf", event.storageUrl());
        }

        @Test
        @DisplayName("should preserve all fields")
        void roundTrip_quandoTodosCampos_preservaDados() throws JsonProcessingException {
            UUID reportId = UUID.randomUUID();
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();
            Instant generatedAt = Instant.parse("2024-01-15T10:30:00Z");

            ReportGeneratedEvent original = new ReportGeneratedEvent(
                    reportId,
                    tenantId,
                    scanId,
                    "HTML",
                    "https://storage.example.com/reports/scan-456.html",
                    generatedAt
            );

            String json = objectMapper.writeValueAsString(original);
            ReportGeneratedEvent deserialized = objectMapper.readValue(json, ReportGeneratedEvent.class);

            assertEquals(original.reportId(), deserialized.reportId());
            assertEquals(original.tenantId(), deserialized.tenantId());
            assertEquals(original.scanId(), deserialized.scanId());
            assertEquals(original.format(), deserialized.format());
            assertEquals(original.storageUrl(), deserialized.storageUrl());
            assertEquals(original.generatedAt(), deserialized.generatedAt());
        }

        @Test
        @DisplayName("should support different formats")
        void serialize_quandoDiferentesFormatos_serializaCorretamente() throws JsonProcessingException {
            String[] formats = {"PDF", "HTML", "JSON", "CSV"};

            for (String format : formats) {
                ReportGeneratedEvent event = new ReportGeneratedEvent(
                        UUID.randomUUID(),
                        TenantId.generate(),
                        UUID.randomUUID(),
                        format,
                        "https://storage.example.com/report",
                        Instant.now()
                );

                String json = objectMapper.writeValueAsString(event);
                ReportGeneratedEvent deserialized = objectMapper.readValue(json, ReportGeneratedEvent.class);

                assertEquals(format, deserialized.format());
            }
        }
    }
}

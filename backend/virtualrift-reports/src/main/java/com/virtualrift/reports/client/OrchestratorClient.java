package com.virtualrift.reports.client;

import com.virtualrift.reports.dto.OrchestratorScanResponse;
import com.virtualrift.reports.dto.OrchestratorScanResultResponse;
import com.virtualrift.reports.exception.ReportGenerationException;
import com.virtualrift.reports.exception.ReportNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Component
public class OrchestratorClient {

    private final RestTemplate restTemplate;
    private final String orchestratorServiceUrl;

    public OrchestratorClient(RestTemplate restTemplate,
                              @Value("${orchestrator.service.url:http://localhost:8083}") String orchestratorServiceUrl) {
        this.restTemplate = restTemplate;
        this.orchestratorServiceUrl = orchestratorServiceUrl;
    }

    public OrchestratorScanResponse getScan(UUID tenantId, UUID scanId) {
        return get(tenantId, scanId, "/api/v1/scans/" + scanId, OrchestratorScanResponse.class);
    }

    public OrchestratorScanResultResponse getScanResult(UUID tenantId, UUID scanId) {
        return get(tenantId, scanId, "/api/v1/scans/" + scanId + "/result", OrchestratorScanResultResponse.class);
    }

    private <T> T get(UUID tenantId, UUID scanId, String path, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", tenantId.toString());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            T response = restTemplate.exchange(
                    orchestratorServiceUrl + path,
                    HttpMethod.GET,
                    entity,
                    responseType
            ).getBody();

            if (response == null) {
                throw new ReportGenerationException("Orchestrator returned an empty response");
            }
            return response;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ReportNotFoundException("Scan not found: " + scanId);
            }
            throw new ReportGenerationException("Failed to fetch scan data from orchestrator", ex);
        } catch (ReportNotFoundException | ReportGenerationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ReportGenerationException("Failed to fetch scan data from orchestrator", ex);
        }
    }
}

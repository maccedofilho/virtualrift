package com.virtualrift.reports.client;

import com.virtualrift.reports.dto.TenantQuotaResponse;
import com.virtualrift.reports.exception.ReportGenerationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Component
public class TenantClient {

    private final RestTemplate restTemplate;
    private final String tenantServiceUrl;

    public TenantClient(RestTemplate restTemplate,
                        @Value("${tenant.service.url:http://localhost:8082}") String tenantServiceUrl) {
        this.restTemplate = restTemplate;
        this.tenantServiceUrl = tenantServiceUrl;
    }

    public TenantQuotaResponse getQuota(UUID tenantId, String rolesHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", tenantId.toString());
        headers.set("X-Roles", rolesHeader);

        try {
            TenantQuotaResponse response = restTemplate.exchange(
                    tenantServiceUrl + "/api/v1/tenants/" + tenantId + "/quota",
                    HttpMethod.GET,
                    new HttpEntity<Void>(headers),
                    TenantQuotaResponse.class
            ).getBody();
            if (response == null || response.reportRetentionDays() < 1) {
                throw new ReportGenerationException("Tenant service returned an invalid report retention policy");
            }
            return response;
        } catch (ReportGenerationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ReportGenerationException("Failed to fetch report retention policy", ex);
        }
    }
}

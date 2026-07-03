package com.virtualrift.tenant.client;

import com.virtualrift.common.model.ScanType;
import com.virtualrift.orchestrator.exception.TenantServiceException;
import com.virtualrift.tenant.model.TenantQuota;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Component
public class TenantClient {

    private final RestTemplate restTemplate;
    private final String tenantServiceUrl;
    private final String internalApiKey;

    public TenantClient(RestTemplate restTemplate,
                        @Value("${tenant.service.url:http://localhost:8082}") String tenantServiceUrl,
                        @Value("${tenant.internal.api-key:virtualrift-dev-onboarding-key}") String internalApiKey) {
        this.restTemplate = restTemplate;
        this.tenantServiceUrl = tenantServiceUrl;
        this.internalApiKey = internalApiKey;
    }

    public TenantQuota getQuota(UUID tenantId) {
        try {
            return restTemplate.getForObject(
                    tenantServiceUrl + "/api/v1/tenants/" + tenantId + "/quota",
                    TenantQuota.class
            );
        } catch (Exception e) {
            throw new TenantServiceException("Failed to fetch quota for tenant: " + tenantId, e);
        }
    }

    public com.virtualrift.tenant.model.Plan getPlan(UUID tenantId) {
        try {
            return restTemplate.getForObject(
                    tenantServiceUrl + "/api/v1/tenants/" + tenantId + "/plan",
                    com.virtualrift.tenant.model.Plan.class
            );
        } catch (Exception e) {
            throw new TenantServiceException("Failed to fetch plan for tenant: " + tenantId, e);
        }
    }

    public boolean isScanTargetAuthorized(UUID tenantId, String target, ScanType scanType) {
        try {
            AuthorizeScanTargetResponse response = restTemplate.postForObject(
                    tenantServiceUrl + "/api/v1/tenants/" + tenantId + "/scan-targets/authorize",
                    new AuthorizeScanTargetRequest(target, scanType.name()),
                    AuthorizeScanTargetResponse.class
            );
            return response != null && response.authorized();
        } catch (Exception e) {
            return false;
        }
    }

    public ResolveScanTargetResponse resolveScanTarget(UUID tenantId, String target, ScanType scanType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Api-Key", internalApiKey);
            HttpEntity<ResolveScanTargetRequest> requestEntity = new HttpEntity<>(
                    new ResolveScanTargetRequest(target, scanType.name()),
                    headers
            );
            ResponseEntity<ResolveScanTargetResponse> response = restTemplate.exchange(
                    tenantServiceUrl + "/api/internal/tenants/" + tenantId + "/scan-targets/resolve",
                    HttpMethod.POST,
                    requestEntity,
                    ResolveScanTargetResponse.class
            );
            ResolveScanTargetResponse body = response.getBody();
            if (body == null) {
                return new ResolveScanTargetResponse(false, Map.of(), Map.of());
            }
            return body;
        } catch (Exception exception) {
            throw new TenantServiceException("Failed to resolve scan target for tenant: " + tenantId, exception);
        }
    }
}

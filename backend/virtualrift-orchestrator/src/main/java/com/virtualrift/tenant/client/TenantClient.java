package com.virtualrift.tenant.client;

import com.virtualrift.common.model.ScanType;
import com.virtualrift.orchestrator.exception.ScanNotFoundException;
import com.virtualrift.tenant.dto.AuthorizeScanTargetRequest;
import com.virtualrift.tenant.dto.AuthorizeScanTargetResponse;
import com.virtualrift.tenant.model.TenantQuota;
import org.springframework.beans.factory.annotation.Value;
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

    public TenantQuota getQuota(UUID tenantId) {
        try {
            return restTemplate.getForObject(
                    tenantServiceUrl + "/api/v1/tenants/" + tenantId + "/quota",
                    TenantQuota.class
            );
        } catch (Exception e) {
            throw new ScanNotFoundException("Failed to fetch quota for tenant: " + tenantId);
        }
    }

    public com.virtualrift.tenant.model.Plan getPlan(UUID tenantId) {
        try {
            return restTemplate.getForObject(
                    tenantServiceUrl + "/api/v1/tenants/" + tenantId + "/plan",
                    com.virtualrift.tenant.model.Plan.class
            );
        } catch (Exception e) {
            throw new ScanNotFoundException("Failed to fetch plan for tenant: " + tenantId);
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
}

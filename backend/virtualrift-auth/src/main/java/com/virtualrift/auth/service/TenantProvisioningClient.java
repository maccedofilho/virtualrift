package com.virtualrift.auth.service;

import com.virtualrift.auth.config.OnboardingConfig;
import com.virtualrift.auth.exception.OnboardingConflictException;
import com.virtualrift.auth.exception.OnboardingProvisioningException;
import com.virtualrift.tenant.dto.TenantResponse;
import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.TenantStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
public class TenantProvisioningClient {

    private final RestClient restClient;
    private final String internalApiKey;

    @Autowired
    public TenantProvisioningClient(OnboardingConfig config) {
        this(
                RestClient.builder()
                        .baseUrl(config.getTenantServiceUrl())
                        .defaultHeader("X-Internal-Api-Key", config.getInternalApiKey())
                        .build(),
                config.getInternalApiKey()
        );
    }

    TenantProvisioningClient(RestClient restClient, String internalApiKey) {
        this.restClient = restClient;
        this.internalApiKey = internalApiKey;
    }

    public boolean isWorkspaceSlugAvailable(String workspaceSlug) {
        try {
            SlugAvailabilityResponse response = restClient.get()
                    .uri("/api/internal/tenants/slug/{slug}/availability", workspaceSlug)
                    .header("X-Internal-Api-Key", internalApiKey)
                    .retrieve()
                    .body(SlugAvailabilityResponse.class);
            return response != null && response.available();
        } catch (RuntimeException ex) {
            throw new OnboardingProvisioningException("Failed to check workspace slug availability", ex);
        }
    }

    public TenantResponse provisionTenant(UUID tenantId, String name, String slug, Plan plan) {
        ProvisionTenantRequest request = new ProvisionTenantRequest(tenantId, name, slug, plan, TenantStatus.ACTIVE);

        try {
            TenantResponse response = restClient.post()
                    .uri("/api/internal/tenants/provision")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (clientRequest, clientResponse) -> {
                        throw new OnboardingConflictException("Workspace slug is already in use");
                    })
                    .body(TenantResponse.class);

            if (response == null) {
                throw new OnboardingProvisioningException("Tenant service returned an empty provisioning response", null);
            }
            return response;
        } catch (OnboardingConflictException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw new OnboardingProvisioningException("Failed to provision workspace in tenant service", ex);
        } catch (RuntimeException ex) {
            throw new OnboardingProvisioningException("Failed to provision workspace in tenant service", ex);
        }
    }

    public void deleteTenant(UUID tenantId) {
        try {
            restClient.delete()
                    .uri("/api/internal/tenants/{tenantId}", tenantId)
                    .header("X-Internal-Api-Key", internalApiKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            throw new OnboardingProvisioningException("Failed to roll back provisioned workspace", ex);
        }
    }

    private record SlugAvailabilityResponse(String slug, boolean available) {
    }

    private record ProvisionTenantRequest(
            UUID id,
            String name,
            String slug,
            Plan plan,
            TenantStatus status
    ) {
    }
}

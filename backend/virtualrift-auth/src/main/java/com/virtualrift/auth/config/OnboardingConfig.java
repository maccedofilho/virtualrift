package com.virtualrift.auth.config;

import com.virtualrift.tenant.model.Plan;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "auth.onboarding")
public class OnboardingConfig {

    private boolean enabled = true;
    private Set<Plan> allowedPlans = new LinkedHashSet<>(Set.of(Plan.TRIAL, Plan.STARTER, Plan.PROFESSIONAL));
    private String tenantServiceUrl = "http://localhost:8082";
    private String internalApiKey = "virtualrift-dev-onboarding-key";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<Plan> getAllowedPlans() {
        return allowedPlans;
    }

    public void setAllowedPlans(Set<Plan> allowedPlans) {
        this.allowedPlans = allowedPlans;
    }

    public String getTenantServiceUrl() {
        return tenantServiceUrl;
    }

    public void setTenantServiceUrl(String tenantServiceUrl) {
        this.tenantServiceUrl = tenantServiceUrl;
    }

    public String getInternalApiKey() {
        return internalApiKey;
    }

    public void setInternalApiKey(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }
}

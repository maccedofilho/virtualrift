package com.virtualrift.tenant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tenant.internal")
public class InternalProvisioningConfig {

    private String apiKey = "virtualrift-dev-onboarding-key";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}

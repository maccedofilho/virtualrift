package com.virtualrift.tenant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tenant.repository-credentials")
public class RepositoryCredentialsConfig {

    private String encryptionKeyBase64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    public String getEncryptionKeyBase64() {
        return encryptionKeyBase64;
    }

    public void setEncryptionKeyBase64(String encryptionKeyBase64) {
        this.encryptionKeyBase64 = encryptionKeyBase64;
    }
}

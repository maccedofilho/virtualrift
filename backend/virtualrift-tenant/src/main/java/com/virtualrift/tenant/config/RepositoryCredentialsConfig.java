package com.virtualrift.tenant.config;

import com.virtualrift.common.runtime.RuntimeConfigGuard;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tenant.repository-credentials")
public class RepositoryCredentialsConfig {

    private String encryptionKeyBase64 = RuntimeConfigGuard.DEFAULT_REPOSITORY_CREDENTIALS_KEY_BASE64;

    public String getEncryptionKeyBase64() {
        return encryptionKeyBase64;
    }

    public void setEncryptionKeyBase64(String encryptionKeyBase64) {
        this.encryptionKeyBase64 = encryptionKeyBase64;
    }
}

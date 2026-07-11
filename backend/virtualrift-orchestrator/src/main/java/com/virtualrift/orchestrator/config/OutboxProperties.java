package com.virtualrift.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outbox")
public class OutboxProperties {

    private String encryptionKeyBase64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private final Publisher publisher = new Publisher();

    public String getEncryptionKeyBase64() {
        return encryptionKeyBase64;
    }

    public void setEncryptionKeyBase64(String encryptionKeyBase64) {
        this.encryptionKeyBase64 = encryptionKeyBase64;
    }

    public Publisher getPublisher() {
        return publisher;
    }

    public static class Publisher {
        private int batchSize = 25;
        private int sendTimeoutSeconds = 10;

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getSendTimeoutSeconds() {
            return sendTimeoutSeconds;
        }

        public void setSendTimeoutSeconds(int sendTimeoutSeconds) {
            this.sendTimeoutSeconds = sendTimeoutSeconds;
        }
    }
}

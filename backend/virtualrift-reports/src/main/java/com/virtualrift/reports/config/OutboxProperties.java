package com.virtualrift.reports.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outbox.publisher")
public class OutboxProperties {

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

package com.virtualrift.apiscanner.config;

import com.virtualrift.apiscanner.engine.ApiClient;
import com.virtualrift.apiscanner.engine.ApiVulnerabilityDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiScannerConfig {

    @Bean
    public ApiVulnerabilityDetector apiVulnerabilityDetector(ApiClient apiClient) {
        return new ApiVulnerabilityDetector(apiClient);
    }
}

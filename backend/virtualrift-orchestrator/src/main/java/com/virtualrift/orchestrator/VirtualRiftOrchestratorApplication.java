package com.virtualrift.orchestrator;

import com.virtualrift.orchestrator.config.OutboxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.virtualrift.orchestrator",
        "com.virtualrift.tenant.client"
})
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class VirtualRiftOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(VirtualRiftOrchestratorApplication.class, args);
    }
}

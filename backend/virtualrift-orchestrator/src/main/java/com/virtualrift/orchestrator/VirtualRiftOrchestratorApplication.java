package com.virtualrift.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.virtualrift.orchestrator",
        "com.virtualrift.tenant.client"
})
public class VirtualRiftOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(VirtualRiftOrchestratorApplication.class, args);
    }
}

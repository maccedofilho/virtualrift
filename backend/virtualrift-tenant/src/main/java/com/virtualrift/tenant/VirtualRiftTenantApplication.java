package com.virtualrift.tenant;

import com.virtualrift.tenant.config.InternalProvisioningConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(InternalProvisioningConfig.class)
public class VirtualRiftTenantApplication {

    public static void main(String[] args) {
        SpringApplication.run(VirtualRiftTenantApplication.class, args);
    }
}

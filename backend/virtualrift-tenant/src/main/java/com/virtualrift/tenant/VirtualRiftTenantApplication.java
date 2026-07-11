package com.virtualrift.tenant;

import com.virtualrift.tenant.config.InternalProvisioningConfig;
import com.virtualrift.tenant.config.RepositoryCredentialsConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({InternalProvisioningConfig.class, RepositoryCredentialsConfig.class})
public class VirtualRiftTenantApplication {

    public static void main(String[] args) {
        SpringApplication.run(VirtualRiftTenantApplication.class, args);
    }
}

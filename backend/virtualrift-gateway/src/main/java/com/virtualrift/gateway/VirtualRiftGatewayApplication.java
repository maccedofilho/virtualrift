package com.virtualrift.gateway;

import com.virtualrift.gateway.config.GatewayConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GatewayConfig.class)
public class VirtualRiftGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(VirtualRiftGatewayApplication.class, args);
    }
}

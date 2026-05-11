package com.virtualrift.auth;

import com.virtualrift.auth.config.OAuthConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OAuthConfig.class)
public class VirtualRiftAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(VirtualRiftAuthApplication.class, args);
    }
}

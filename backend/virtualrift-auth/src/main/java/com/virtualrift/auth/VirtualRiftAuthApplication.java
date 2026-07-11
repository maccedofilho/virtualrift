package com.virtualrift.auth;

import com.virtualrift.auth.config.OAuthConfig;
import com.virtualrift.auth.config.OnboardingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({OAuthConfig.class, OnboardingConfig.class})
public class VirtualRiftAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(VirtualRiftAuthApplication.class, args);
    }
}

package com.virtualrift.reports;

import com.virtualrift.reports.config.OutboxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class VirtualRiftReportsApplication {

    public static void main(String[] args) {
        SpringApplication.run(VirtualRiftReportsApplication.class, args);
    }
}

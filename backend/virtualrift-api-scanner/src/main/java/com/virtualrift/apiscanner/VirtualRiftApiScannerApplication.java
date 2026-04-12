package com.virtualrift.apiscanner;

import com.virtualrift.apiscanner.config.ApiScannerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ApiScannerProperties.class)
public class VirtualRiftApiScannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(VirtualRiftApiScannerApplication.class, args);
    }
}

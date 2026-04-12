package com.virtualrift.webscanner;

import com.virtualrift.webscanner.config.WebScannerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WebScannerProperties.class)
public class VirtualRiftWebScannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(VirtualRiftWebScannerApplication.class, args);
    }
}

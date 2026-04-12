package com.virtualrift.networkscanner;

import com.virtualrift.networkscanner.config.NetworkScannerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(NetworkScannerProperties.class)
public class VirtualRiftNetworkScannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(VirtualRiftNetworkScannerApplication.class, args);
    }
}

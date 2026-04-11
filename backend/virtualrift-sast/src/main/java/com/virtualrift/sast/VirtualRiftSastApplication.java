package com.virtualrift.sast;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VirtualRiftSastApplication {

    public static void main(String[] args) {
        SpringApplication.run(VirtualRiftSastApplication.class, args);
    }
}

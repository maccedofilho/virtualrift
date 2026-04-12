package com.virtualrift.webscanner.config;

import com.virtualrift.webscanner.engine.HttpClient;
import com.virtualrift.webscanner.engine.SqlInjectionDetector;
import com.virtualrift.webscanner.engine.XssDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebScannerConfig {

    @Bean
    public XssDetector xssDetector(HttpClient httpClient) {
        return new XssDetector(httpClient);
    }

    @Bean
    public SqlInjectionDetector sqlInjectionDetector(HttpClient httpClient) {
        return new SqlInjectionDetector(httpClient);
    }
}

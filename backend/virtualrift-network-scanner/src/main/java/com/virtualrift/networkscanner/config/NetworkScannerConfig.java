package com.virtualrift.networkscanner.config;

import com.virtualrift.networkscanner.engine.TlsAnalyzer;
import com.virtualrift.networkscanner.engine.TlsConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NetworkScannerConfig {

    @Bean
    public TlsAnalyzer tlsAnalyzer(TlsConnection tlsConnection) {
        return new TlsAnalyzer(tlsConnection);
    }
}

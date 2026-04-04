package com.virtualrift.auth.config;

import com.virtualrift.auth.service.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Value("${auth.jwt.private-key}")
    private String privateKey;

    @Value("${auth.jwt.public-key}")
    private String publicKey;

    @Bean
    public JwtService jwtService() {
        return new JwtService(privateKey, publicKey);
    }
}

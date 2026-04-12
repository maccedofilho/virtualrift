package com.virtualrift.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("VirtualRift Auth API")
                        .version("v1")
                        .description("Autenticacao, refresh e logout de sessoes da plataforma VirtualRift."));
    }
}

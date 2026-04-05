package com.virtualrift.gateway.config;

import com.virtualrift.gateway.filter.JwtValidationFilter;
import com.virtualrift.gateway.filter.RateLimitFilter;
import com.virtualrift.gateway.filter.ReactiveTokenDenylist;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.nio.charset.StandardCharsets;

@Configuration
public class FilterConfig {

    @Bean
    public JwtValidationFilter jwtValidationFilter(JwtConfig.JwtValidator jwtValidator,
                                                    ReactiveTokenDenylist tokenDenylist,
                                                    GatewayConfig gatewayConfig) {
        return new JwtValidationFilter(jwtValidator, tokenDenylist, gatewayConfig);
    }

    @Bean
    public RateLimitFilter rateLimitFilter(ProxyManager<String> proxyManager,
                                           GatewayConfig gatewayConfig) {
        return new RateLimitFilter(proxyManager, gatewayConfig);
    }

    @Bean
    public ProxyManager<String> proxyManager(LettuceConnectionFactory connectionFactory) {
        RedisClient redisClient = RedisClient.create(
                RedisURI.create(
                        connectionFactory.getStandaloneConfiguration().getHostName(),
                        connectionFactory.getStandaloneConfiguration().getPort()
                )
        );

        return LettuceBasedProxyManager.builderFor(redisClient)
                .withKeyMapper(key -> key.getBytes(StandardCharsets.UTF_8))
                .build();
    }
}

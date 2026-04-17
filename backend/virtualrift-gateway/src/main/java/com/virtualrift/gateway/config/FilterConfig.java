package com.virtualrift.gateway.config;

import com.virtualrift.gateway.filter.JwtValidationFilter;
import com.virtualrift.gateway.filter.RateLimitFilter;
import com.virtualrift.gateway.filter.ReactiveTokenDenylist;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;


@Configuration
public class FilterConfig {

    @Bean
    public JwtValidationFilter jwtValidationFilter(JwtConfig.JwtValidator jwtValidator,
                                                    ReactiveTokenDenylist tokenDenylist,
                                                    GatewayConfig gatewayConfig) {
        return new JwtValidationFilter(jwtValidator, tokenDenylist, gatewayConfig);
    }

    @Bean
    public RateLimitFilter rateLimitFilter(ProxyManager<byte[]> proxyManager,
                                           GatewayConfig gatewayConfig) {
        return new RateLimitFilter(proxyManager, gatewayConfig);
    }

    @Bean
    public ProxyManager<byte[]> proxyManager(
            @Qualifier("redisConnectionFactory") LettuceConnectionFactory connectionFactory,
            GatewayConfig gatewayConfig) {
        RedisClient redisClient = RedisClient.create(
                RedisURI.create(
                        connectionFactory.getStandaloneConfiguration().getHostName(),
                        connectionFactory.getStandaloneConfiguration().getPort()
                )
        );

        return LettuceBasedProxyManager.builderFor(redisClient)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                        rateLimitExpiration(gatewayConfig)
                ))
                .build();
    }

    Duration rateLimitExpiration(GatewayConfig gatewayConfig) {
        return Duration.ofSeconds(Math.max(1, gatewayConfig.getRateLimit().getWindowDuration()));
    }
}

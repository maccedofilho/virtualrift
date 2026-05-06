package com.virtualrift.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "gateway")
public class GatewayConfig {

    private Security security = new Security();
    private RateLimit rateLimit = new RateLimit();
    private Cors cors = new Cors();

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public static class Security {
        private List<String> publicPaths = List.of("/health", "/actuator/**", "/api/v1/auth/refresh");
        private long tokenCacheTtl = 300;
        private String denylistKeyPrefix = "auth:denylist:";

        public List<String> getPublicPaths() {
            return publicPaths;
        }

        public void setPublicPaths(List<String> publicPaths) {
            this.publicPaths = publicPaths;
        }

        public long getTokenCacheTtl() {
            return tokenCacheTtl;
        }

        public void setTokenCacheTtl(long tokenCacheTtl) {
            this.tokenCacheTtl = tokenCacheTtl;
        }

        public String getDenylistKeyPrefix() {
            return denylistKeyPrefix;
        }

        public void setDenylistKeyPrefix(String denylistKeyPrefix) {
            this.denylistKeyPrefix = denylistKeyPrefix;
        }
    }

    public static class RateLimit {
        private int defaultLimit = 1000;
        private int scanLimit = 100;
        private int burstCapacity = 10;
        private int refillRate = 1;
        private int windowDuration = 60;
        private String redisKeyPrefix = "ratelimit:";
        private boolean enabled = true;

        public int getDefaultLimit() {
            return defaultLimit;
        }

        public void setDefaultLimit(int defaultLimit) {
            this.defaultLimit = defaultLimit;
        }

        public int getScanLimit() {
            return scanLimit;
        }

        public void setScanLimit(int scanLimit) {
            this.scanLimit = scanLimit;
        }

        public int getBurstCapacity() {
            return burstCapacity;
        }

        public void setBurstCapacity(int burstCapacity) {
            this.burstCapacity = burstCapacity;
        }

        public int getRefillRate() {
            return refillRate;
        }

        public void setRefillRate(int refillRate) {
            this.refillRate = refillRate;
        }

        public int getWindowDuration() {
            return windowDuration;
        }

        public void setWindowDuration(int windowDuration) {
            this.windowDuration = windowDuration;
        }

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173");
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        private List<String> allowedHeaders = List.of("*");
        private boolean allowCredentials = true;
        private long maxAge = 3600;

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        public long getMaxAge() {
            return maxAge;
        }

        public void setMaxAge(long maxAge) {
            this.maxAge = maxAge;
        }
    }
}

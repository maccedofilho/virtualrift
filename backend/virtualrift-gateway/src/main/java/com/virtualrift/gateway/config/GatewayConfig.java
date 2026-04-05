package com.virtualrift.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "gateway")
public class GatewayConfig {

    private Security security = new Security();
    private RateLimit rateLimit = new RateLimit();

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

    public static class Security {
        private List<String> publicPaths = List.of("/health", "/actuator/**");
        private long tokenCacheTtl = 300;
        private String denylistKeyPrefix = "denylist:";

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
}

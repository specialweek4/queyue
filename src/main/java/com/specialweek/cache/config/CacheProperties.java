package com.specialweek.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cache")
@Data
public class CacheProperties {

    private L2 l2 = new L2();

    @Data
    public static class L2 {
        private PublicCfg publicCfg = new PublicCfg();
    }

    @Data
    public static class PublicCfg {
        private int ttlSeconds = 5;
        private long maxSize = 32;
    }
}

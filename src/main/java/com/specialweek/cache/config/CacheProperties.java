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
        private MineCfg mineCfg = new MineCfg();
        private BlogDetailCfg blogDetail = new BlogDetailCfg();
        private ProductPublicCfg productPublic = new ProductPublicCfg();
        private ProductMineCfg productMine = new ProductMineCfg();
        private ProductDetailCfg productDetail = new ProductDetailCfg();
    }

    @Data
    public static class PublicCfg {
        private int ttlSeconds = 15;
        private long maxSize = 1000;
    }

    @Data
    public static class BlogDetailCfg {
        private int ttlSeconds = 30;
        private long maxSize = 5000;
    }


    @Data
    public static class MineCfg {
        // TTL（秒）：写入后在本地缓存中保留的时长。
        private int ttlSeconds = 10;
        // 最大条目数：超过后按 Caffeine 策略逐出。
        private long maxSize = 1000;
    }
    @Data
    public static class ProductPublicCfg {
        private int ttlSeconds = 15;
        private long maxSize = 1000;
    }

    @Data
    public static class ProductMineCfg {
        private int ttlSeconds = 10;
        private long maxSize = 1000;
    }

    @Data
    public static class ProductDetailCfg {
        private int ttlSeconds = 30;
        private long maxSize = 5000;
    }
}

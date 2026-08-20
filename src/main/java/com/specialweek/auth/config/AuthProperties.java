package com.specialweek.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "auth.jwt")
public class AuthProperties {

    private String issuer = "queyue";
    private String keyId = "queyue-key";
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(7);
    private Resource privateKey;
    private Resource publicKey;
}

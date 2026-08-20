package com.specialweek.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    private final AuthProperties properties;

    @Bean
    public JwtEncoder jwtEncoder() {
        RSAPrivateKey privateKey = PemUtils.readPrivateKey(properties.getPrivateKey());
        RSAPublicKey publicKey = PemUtils.readPublicKey(properties.getPublicKey());

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(properties.getKeyId())
                .build();
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(source);
    }


    @Bean("accessJwtDecoder")
    public JwtDecoder accessJwtDecoder() {
        NimbusJwtDecoder decoder = newDecoder();
        OAuth2TokenValidator<Jwt> issuerAndTime =
                JwtValidators.createDefaultWithIssuer(properties.getIssuer());
        OAuth2TokenValidator<Jwt> accessType =
                new JwtClaimValidator<>("token_type", "access"::equals);
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(issuerAndTime, accessType)
        );
        return decoder;
    }


    @Bean("refreshJwtDecoder")
    public JwtDecoder refreshJwtDecoder() {
        NimbusJwtDecoder decoder = newDecoder();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.getIssuer()));
        return decoder;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private NimbusJwtDecoder newDecoder() {
        RSAPublicKey publicKey = PemUtils.readPublicKey(properties.getPublicKey());
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
}

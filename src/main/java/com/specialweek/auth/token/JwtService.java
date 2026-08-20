package com.specialweek.auth.token;

import com.specialweek.auth.config.AuthProperties;
import com.specialweek.user.domain.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder refreshJwtDecoder;
    private final AuthProperties properties;

    public JwtService(
            JwtEncoder jwtEncoder,
            @Qualifier("refreshJwtDecoder") JwtDecoder refreshJwtDecoder,
            AuthProperties properties
    ) {
        this.jwtEncoder = jwtEncoder;
        this.refreshJwtDecoder = refreshJwtDecoder;
        this.properties = properties;
    }

    public TokenPair issueTokenPair(User user) {
        Instant issuedAt = Instant.now();
        Instant accessExpiresAt = issuedAt.plus(properties.getAccessTokenTtl());
        Instant refreshExpiresAt = issuedAt.plus(properties.getRefreshTokenTtl());
        String refreshTokenId = UUID.randomUUID().toString();

        String accessToken = encode(
                user.getId(), "access", UUID.randomUUID().toString(),
                issuedAt, accessExpiresAt
        );
        String refreshToken = encode(
                user.getId(), "refresh", refreshTokenId,
                issuedAt, refreshExpiresAt
        );

        return new TokenPair(
                accessToken, accessExpiresAt,
                refreshToken, refreshExpiresAt,
                refreshTokenId
        );
    }

    public Jwt decodeRefreshToken(String token) {
        return refreshJwtDecoder.decode(token);
    }

    public long extractUserId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public String extractTokenType(Jwt jwt) {
        return jwt.getClaimAsString("token_type");
    }

    private String encode(
            long userId,
            String tokenType,
            String tokenId,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(properties.getKeyId())
                .build();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .id(tokenId)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("uid", userId)
                .claim("token_type", tokenType)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}

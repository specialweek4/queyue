package com.specialweek.auth.token;

import java.time.Duration;

public interface RefreshTokenStore {

    void store(long userId, String tokenId, Duration ttl);

    boolean consume(long userId, String tokenId);

    void revoke(long userId, String tokenId);

    void revokeAll(long userId);
}

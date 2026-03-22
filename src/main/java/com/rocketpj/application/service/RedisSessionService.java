package com.rocketpj.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSessionService {

    private final StringRedisTemplate redisTemplate;

    private static final String USER_SESSIONS_PREFIX = "user_sessions:";
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String BLACKLIST_PREFIX = "blacklist:";

    // --- User Sessions ---

    public void addSession(UUID userId, String sessionId, Duration ttl) {
        String key = USER_SESSIONS_PREFIX + userId;
        redisTemplate.opsForSet().add(key, sessionId);
        redisTemplate.expire(key, ttl);
    }

    public void removeSession(UUID userId, String sessionId) {
        redisTemplate.opsForSet().remove(USER_SESSIONS_PREFIX + userId, sessionId);
    }

    public Set<String> getActiveSessions(UUID userId) {
        return redisTemplate.opsForSet().members(USER_SESSIONS_PREFIX + userId);
    }

    public void removeAllSessions(UUID userId) {
        redisTemplate.delete(USER_SESSIONS_PREFIX + userId);
    }

    // --- Refresh Token ---

    public void storeRefreshToken(String tokenHash, String value, Duration ttl) {
        redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + tokenHash, value, ttl);
    }

    public String getRefreshToken(String tokenHash) {
        return redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + tokenHash);
    }

    public void removeRefreshToken(String tokenHash) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + tokenHash);
    }

    // --- Access Token Blacklist ---

    public void blacklistAccessToken(String jti, Duration ttl) {
        redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", ttl);
    }

    public boolean isAccessTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }
}

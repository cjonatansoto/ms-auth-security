package com.rocketpj.application.service;

import com.rocketpj.application.dto.*;
import com.rocketpj.application.entity.*;
import com.rocketpj.application.exception.BusinessException;
import com.rocketpj.application.exception.EnumError;
import com.rocketpj.application.repository.*;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RedisSessionService redisSessionService;
    private final AuditService auditService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request, String ipAddress, String userAgent) {
        if (userRepository.existsByEmail(request.email())) {
            throw BusinessException.from(EnumError.EMAIL_ALREADY_EXISTS)
                    .conflict()
                    .build();
        }

        var defaultRole = roleRepository.findByCode("ROLE_USER")
                .orElseThrow(() -> BusinessException.from(EnumError.ROLE_NOT_FOUND)
                        .internalServerError()
                        .build());

        var user = UserEntity.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .lastname(request.lastname())
                .phone(request.phone())
                .roles(Set.of(defaultRole))
                .build();

        user = userRepository.save(user);

        auditService.log(user.getId(), "REGISTER", "User registered", ipAddress, userAgent);

        return createSessionAndTokens(user, ipAddress, userAgent, null);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent, String deviceInfo) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException e) {
            auditService.log(null, "LOGIN_FAILED", "Invalid credentials for: " + request.email(), ipAddress, userAgent);
            throw BusinessException.from(EnumError.INVALID_CREDENTIALS)
                    .unauthorized()
                    .build();
        } catch (DisabledException e) {
            throw BusinessException.from(EnumError.ACCOUNT_NOT_ACTIVE)
                    .forbidden()
                    .build();
        } catch (LockedException e) {
            throw BusinessException.from(EnumError.ACCOUNT_LOCKED)
                    .forbidden()
                    .build();
        } catch (AccountExpiredException e) {
            throw BusinessException.from(EnumError.ACCOUNT_EXPIRED)
                    .forbidden()
                    .build();
        } catch (CredentialsExpiredException e) {
            throw BusinessException.from(EnumError.CREDENTIALS_EXPIRED)
                    .unauthorized()
                    .build();
        }

        var user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> BusinessException.from(EnumError.INVALID_CREDENTIALS)
                        .unauthorized()
                        .build());

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        auditService.log(user.getId(), "LOGIN_SUCCESS", "User logged in", ipAddress, userAgent);

        return createSessionAndTokens(user, ipAddress, userAgent, deviceInfo);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request, String ipAddress, String userAgent) {
        String rawRefreshToken = request.refreshToken();
        String tokenHash = JwtService.hashToken(rawRefreshToken);

        // 1. Find the refresh token record by hash
        var tokenEntity = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> BusinessException.from(EnumError.INVALID_TOKEN)
                        .unauthorized()
                        .build());

        // 2. REUSE DETECTION: if this token is already revoked, it means someone reused it
        if (tokenEntity.isRevoked()) {
            log.warn("REFRESH TOKEN REUSE DETECTED! tokenFamily={}, userId={}",
                    tokenEntity.getTokenFamily(), tokenEntity.getUser().getId());

            tokenEntity.markReuseDetected();
            refreshTokenRepository.save(tokenEntity);

            // Revoke entire token family
            refreshTokenRepository.revokeAllByTokenFamily(tokenEntity.getTokenFamily(), Instant.now());

            // Revoke the associated session
            var session = tokenEntity.getSession();
            session.revoke();
            authSessionRepository.save(session);

            // Revoke ALL user sessions (compromise detected)
            UUID userId = tokenEntity.getUser().getId();
            authSessionRepository.revokeAllByUserId(userId, Instant.now());
            refreshTokenRepository.revokeAllByUserId(userId, Instant.now());

            // Clean Redis
            redisSessionService.removeAllSessions(userId);

            auditService.log(userId, "REFRESH_REUSE_DETECTED",
                    "Token reuse detected, all sessions revoked. Family: " + tokenEntity.getTokenFamily(),
                    ipAddress, userAgent);

            throw BusinessException.from(EnumError.REFRESH_TOKEN_REUSE)
                    .unauthorized()
                    .build();
        }

        // 3. Validate the JWT itself
        Claims claims;
        try {
            claims = jwtService.parseToken(rawRefreshToken);
        } catch (Exception e) {
            // Token might be expired or tampered
            tokenEntity.revoke();
            refreshTokenRepository.save(tokenEntity);

            if (jwtService.isTokenExpired(rawRefreshToken)) {
                throw BusinessException.from(EnumError.TOKEN_EXPIRED)
                        .unauthorized()
                        .build();
            }
            throw BusinessException.from(EnumError.INVALID_TOKEN)
                    .unauthorized()
                    .build();
        }

        // 4. Verify it's a refresh token
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw BusinessException.from(EnumError.INVALID_TOKEN)
                    .unauthorized()
                    .build();
        }

        // 5. Get user and session
        var user = tokenEntity.getUser();
        var session = tokenEntity.getSession();

        if (session.isRevoked()) {
            throw BusinessException.from(EnumError.SESSION_NOT_FOUND)
                    .unauthorized()
                    .build();
        }

        // 6. Revoke the old refresh token
        tokenEntity.setLastUsedAt(Instant.now());
        tokenEntity.revoke();
        refreshTokenRepository.save(tokenEntity);

        // Remove old token from Redis
        redisSessionService.removeRefreshToken(tokenHash);

        // 7. Generate new tokens (rotation)
        Set<String> roles = user.getRoles().stream()
                .map(RoleEntity::getCode)
                .collect(Collectors.toSet());

        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles);
        String newRefreshToken = jwtService.generateRefreshToken(user.getId(), session.getId(), tokenEntity.getTokenFamily());
        String newTokenHash = JwtService.hashToken(newRefreshToken);

        // 8. Persist new refresh token with parent link
        var newTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .session(session)
                .tokenHash(newTokenHash)
                .tokenFamily(tokenEntity.getTokenFamily())
                .parentToken(tokenEntity)
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()))
                .build();
        refreshTokenRepository.save(newTokenEntity);

        // 9. Update session
        session.setRefreshTokenVersion(session.getRefreshTokenVersion() + 1);
        session.setLastUsedAt(Instant.now());
        authSessionRepository.save(session);

        // 10. Update Redis
        Duration refreshTtl = Duration.ofMillis(jwtService.getRefreshTokenExpirationMs());
        redisSessionService.storeRefreshToken(newTokenHash, user.getId().toString(), refreshTtl);

        auditService.log(user.getId(), "REFRESH_SUCCESS", "Token rotated successfully", ipAddress, userAgent);

        return new AuthResponse(
                newAccessToken,
                newRefreshToken,
                jwtService.getAccessTokenExpirationMs() / 1000,
                toUserInfoResponse(user)
        );
    }

    @Transactional
    public void logout(UUID userId, String accessTokenJti, String refreshToken, String ipAddress, String userAgent) {
        // Blacklist the access token in Redis
        if (accessTokenJti != null) {
            Duration ttl = Duration.ofMillis(jwtService.getAccessTokenExpirationMs());
            redisSessionService.blacklistAccessToken(accessTokenJti, ttl);
        }

        // Revoke the refresh token if provided
        if (refreshToken != null && !refreshToken.isBlank()) {
            String tokenHash = JwtService.hashToken(refreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                token.revoke();
                refreshTokenRepository.save(token);

                // Revoke the session
                var session = token.getSession();
                session.revoke();
                authSessionRepository.save(session);

                // Clean Redis
                redisSessionService.removeRefreshToken(tokenHash);
                redisSessionService.removeSession(userId, session.getSessionIdentifier());
            });
        }

        auditService.log(userId, "LOGOUT", "User logged out", ipAddress, userAgent);
    }

    public UserInfoResponse getCurrentUser(UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.from(EnumError.NOT_FOUND)
                        .notFound()
                        .build());
        return toUserInfoResponse(user);
    }

    // --- Private helpers ---

    private AuthResponse createSessionAndTokens(UserEntity user, String ipAddress, String userAgent, String deviceInfo) {
        Set<String> roles = user.getRoles().stream()
                .map(RoleEntity::getCode)
                .collect(Collectors.toSet());

        // Create session
        String sessionIdentifier = UUID.randomUUID().toString();
        var session = AuthSessionEntity.builder()
                .user(user)
                .sessionIdentifier(sessionIdentifier)
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()))
                .build();
        session = authSessionRepository.save(session);

        // Generate tokens
        UUID tokenFamily = UUID.randomUUID();
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles);
        String refreshToken = jwtService.generateRefreshToken(user.getId(), session.getId(), tokenFamily);
        String tokenHash = JwtService.hashToken(refreshToken);

        // Persist refresh token
        var refreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .session(session)
                .tokenHash(tokenHash)
                .tokenFamily(tokenFamily)
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()))
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        // Store in Redis
        Duration refreshTtl = Duration.ofMillis(jwtService.getRefreshTokenExpirationMs());
        redisSessionService.addSession(user.getId(), sessionIdentifier, refreshTtl);
        redisSessionService.storeRefreshToken(tokenHash, user.getId().toString(), refreshTtl);

        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtService.getAccessTokenExpirationMs() / 1000,
                toUserInfoResponse(user)
        );
    }

    private UserInfoResponse toUserInfoResponse(UserEntity user) {
        Set<String> roles = user.getRoles().stream()
                .map(RoleEntity::getCode)
                .collect(Collectors.toSet());

        return new UserInfoResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getLastname(),
                user.getPhone(),
                roles
        );
    }
}

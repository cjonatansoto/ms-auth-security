package com.rocketpj.application.service;

import com.rocketpj.application.dto.*;
import com.rocketpj.application.entity.*;
import com.rocketpj.application.exception.BusinessException;
import com.rocketpj.application.exception.EnumError;
import com.rocketpj.application.repository.*;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AuthSessionRepository authSessionRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RedisSessionService redisSessionService;
    @Mock private AuditService auditService;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private static final String IP = "127.0.0.1";
    private static final String UA = "TestAgent/1.0";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "Passw0rd!";

    private RoleEntity defaultRole;
    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        defaultRole = RoleEntity.builder()
                .id(UUID.randomUUID())
                .code("ROLE_USER")
                .name("User")
                .build();

        testUser = UserEntity.builder()
                .id(USER_ID)
                .email(EMAIL)
                .passwordHash("$2a$10$encoded")
                .name("Test")
                .lastname("User")
                .enabled(true)
                .accountNonLocked(true)
                .accountNonExpired(true)
                .credentialsNonExpired(true)
                .roles(Set.of(defaultRole))
                .build();
    }

    @Nested
    @DisplayName("Register")
    class RegisterTests {

        @Test
        @DisplayName("Should register user successfully")
        void shouldRegisterSuccessfully() {
            var request = new RegisterRequest(EMAIL, PASSWORD, "Test", "User", null);

            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(roleRepository.findByCode("ROLE_USER")).thenReturn(Optional.of(defaultRole));
            when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$encoded");
            when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
                UserEntity u = inv.getArgument(0);
                u.setId(USER_ID);
                return u;
            });
            when(authSessionRepository.save(any(AuthSessionEntity.class))).thenAnswer(inv -> {
                AuthSessionEntity s = inv.getArgument(0);
                s.setId(UUID.randomUUID());
                return s;
            });
            when(refreshTokenRepository.save(any(RefreshTokenEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken(any(), eq(EMAIL), anySet())).thenReturn("access-token");
            when(jwtService.generateRefreshToken(any(), any(), any())).thenReturn("refresh-token");
            when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);
            when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

            AuthResponse response = authService.register(request, IP, UA);

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
            assertThat(response.user().email()).isEqualTo(EMAIL);
            verify(userRepository).save(any(UserEntity.class));
            verify(auditService).log(any(), eq("REGISTER"), anyString(), eq(IP), eq(UA));
        }

        @Test
        @DisplayName("Should fail when email already exists")
        void shouldFailWhenEmailExists() {
            var request = new RegisterRequest(EMAIL, PASSWORD, "Test", null, null);
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            var ex = catchThrowableOfType(
                    () -> authService.register(request, IP, UA),
                    BusinessException.class
            );

            assertThat(ex.getErrorEnum()).isEqualTo(EnumError.EMAIL_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("Login")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully")
        void shouldLoginSuccessfully() {
            var request = new LoginRequest(EMAIL, PASSWORD);

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD));
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);
            when(authSessionRepository.save(any(AuthSessionEntity.class))).thenAnswer(inv -> {
                AuthSessionEntity s = inv.getArgument(0);
                s.setId(UUID.randomUUID());
                return s;
            });
            when(refreshTokenRepository.save(any(RefreshTokenEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken(eq(USER_ID), eq(EMAIL), anySet())).thenReturn("access-token");
            when(jwtService.generateRefreshToken(any(), any(), any())).thenReturn("refresh-token");
            when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);
            when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

            AuthResponse response = authService.login(request, IP, UA, null);

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
            verify(auditService).log(eq(USER_ID), eq("LOGIN_SUCCESS"), anyString(), eq(IP), eq(UA));
        }

        @Test
        @DisplayName("Should fail with invalid credentials")
        void shouldFailWithInvalidCredentials() {
            var request = new LoginRequest(EMAIL, "wrong-password");

            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            var ex = catchThrowableOfType(
                    () -> authService.login(request, IP, UA, null),
                    BusinessException.class
            );

            assertThat(ex.getErrorEnum()).isEqualTo(EnumError.INVALID_CREDENTIALS);
            verify(auditService).log(isNull(), eq("LOGIN_FAILED"), anyString(), eq(IP), eq(UA));
        }
    }

    @Nested
    @DisplayName("Refresh Token Rotation")
    class RefreshTests {

        private AuthSessionEntity session;
        private RefreshTokenEntity tokenEntity;
        private String rawRefreshToken = "raw-refresh-token";
        private String tokenHash = JwtService.hashToken(rawRefreshToken);
        private UUID tokenFamily = UUID.randomUUID();

        @BeforeEach
        void setUp() {
            session = AuthSessionEntity.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .sessionIdentifier(UUID.randomUUID().toString())
                    .refreshTokenVersion(0)
                    .revoked(false)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            tokenEntity = RefreshTokenEntity.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .session(session)
                    .tokenHash(tokenHash)
                    .tokenFamily(tokenFamily)
                    .revoked(false)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        }

        @Test
        @DisplayName("Should rotate refresh token successfully")
        void shouldRotateTokenSuccessfully() {
            var request = new RefreshTokenRequest(rawRefreshToken);

            Claims mockClaims = mock(Claims.class);
            when(mockClaims.get("type", String.class)).thenReturn("refresh");

            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));
            when(jwtService.parseToken(rawRefreshToken)).thenReturn(mockClaims);
            when(refreshTokenRepository.save(any(RefreshTokenEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(authSessionRepository.save(any(AuthSessionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken(eq(USER_ID), eq(EMAIL), anySet())).thenReturn("new-access-token");
            when(jwtService.generateRefreshToken(eq(USER_ID), any(), eq(tokenFamily))).thenReturn("new-refresh-token");
            when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);
            when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

            AuthResponse response = authService.refresh(request, IP, UA);

            assertThat(response.accessToken()).isEqualTo("new-access-token");
            assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
            assertThat(tokenEntity.isRevoked()).isTrue();
            verify(auditService).log(eq(USER_ID), eq("REFRESH_SUCCESS"), anyString(), eq(IP), eq(UA));
            verify(refreshTokenRepository, times(2)).save(any(RefreshTokenEntity.class));
        }

        @Test
        @DisplayName("Should detect reuse and revoke all sessions")
        void shouldDetectReuseAndRevokeAll() {
            // Mark token as already revoked (simulating reuse)
            tokenEntity.revoke();
            var request = new RefreshTokenRequest(rawRefreshToken);

            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));

            var ex = catchThrowableOfType(
                    () -> authService.refresh(request, IP, UA),
                    BusinessException.class
            );

            assertThat(ex.getErrorEnum()).isEqualTo(EnumError.REFRESH_TOKEN_REUSE);

            // Verify all revocations happened
            verify(refreshTokenRepository).revokeAllByTokenFamily(eq(tokenFamily), any(Instant.class));
            verify(authSessionRepository).revokeAllByUserId(eq(USER_ID), any(Instant.class));
            verify(refreshTokenRepository).revokeAllByUserId(eq(USER_ID), any(Instant.class));
            verify(redisSessionService).removeAllSessions(USER_ID);
            verify(auditService).log(eq(USER_ID), eq("REFRESH_REUSE_DETECTED"), anyString(), eq(IP), eq(UA));
        }

        @Test
        @DisplayName("Should fail when token not found")
        void shouldFailWhenTokenNotFound() {
            var request = new RefreshTokenRequest("unknown-token");
            String hash = JwtService.hashToken("unknown-token");

            when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.empty());

            var ex = catchThrowableOfType(
                    () -> authService.refresh(request, IP, UA),
                    BusinessException.class
            );

            assertThat(ex.getErrorEnum()).isEqualTo(EnumError.INVALID_TOKEN);
        }
    }

    @Nested
    @DisplayName("Logout")
    class LogoutTests {

        @Test
        @DisplayName("Should logout successfully")
        void shouldLogoutSuccessfully() {
            String rawRefreshToken = "refresh-token";
            String tokenHash = JwtService.hashToken(rawRefreshToken);
            UUID sessionId = UUID.randomUUID();

            var session = AuthSessionEntity.builder()
                    .id(sessionId)
                    .user(testUser)
                    .sessionIdentifier("session-123")
                    .revoked(false)
                    .build();

            var tokenEntity = RefreshTokenEntity.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .session(session)
                    .tokenHash(tokenHash)
                    .revoked(false)
                    .build();

            when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);
            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));
            when(refreshTokenRepository.save(any(RefreshTokenEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(authSessionRepository.save(any(AuthSessionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.logout(USER_ID, "jti-123", rawRefreshToken, IP, UA);

            verify(redisSessionService).blacklistAccessToken(eq("jti-123"), any(Duration.class));
            verify(redisSessionService).removeRefreshToken(tokenHash);
            verify(redisSessionService).removeSession(USER_ID, "session-123");
            assertThat(tokenEntity.isRevoked()).isTrue();
            assertThat(session.isRevoked()).isTrue();
            verify(auditService).log(eq(USER_ID), eq("LOGOUT"), anyString(), eq(IP), eq(UA));
        }
    }
}

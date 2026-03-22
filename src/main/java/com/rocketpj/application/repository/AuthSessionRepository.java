package com.rocketpj.application.repository;

import com.rocketpj.application.entity.AuthSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, UUID> {

    Optional<AuthSessionEntity> findBySessionIdentifier(String sessionIdentifier);

    List<AuthSessionEntity> findByUserIdAndRevokedFalse(UUID userId);

    @Modifying
    @Query("UPDATE AuthSessionEntity s SET s.revoked = true, s.revokedAt = :now WHERE s.user.id = :userId AND s.revoked = false")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}

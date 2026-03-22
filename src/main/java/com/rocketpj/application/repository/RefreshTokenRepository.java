package com.rocketpj.application.repository;

import com.rocketpj.application.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshTokenEntity t SET t.revoked = true, t.revokedAt = :now WHERE t.tokenFamily = :family AND t.revoked = false")
    int revokeAllByTokenFamily(@Param("family") UUID family, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE RefreshTokenEntity t SET t.revoked = true, t.revokedAt = :now WHERE t.user.id = :userId AND t.revoked = false")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}

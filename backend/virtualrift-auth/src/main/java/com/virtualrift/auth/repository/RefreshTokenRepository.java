package com.virtualrift.auth.repository;

import com.virtualrift.auth.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByUserId(UUID userId);

    void deleteByExpirationBefore(Instant expiration);

    void deleteByUserId(UUID userId);
}

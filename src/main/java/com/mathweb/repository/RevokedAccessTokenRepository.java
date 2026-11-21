package com.mathweb.repository;

import com.mathweb.entity.RevokedAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface RevokedAccessTokenRepository
        extends JpaRepository<RevokedAccessToken, String> {

    boolean existsByJtiAndExpiresAtAfter(String jti, Instant now);

    long deleteByExpiresAtBefore(Instant now);
}
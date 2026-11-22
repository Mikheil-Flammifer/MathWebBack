package com.mathweb.repository;

import com.mathweb.entity.RefreshToken;
import com.mathweb.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r join fetch r.user where r.token = :token")
    Optional<RefreshToken> findByTokenForUpdate(@Param("token") String token);

    @Query(value = """
            select * from refresh_tokens
            where user_id = :#{#user.id}
            order by created_at desc, id desc
            limit 1
            """, nativeQuery = true)
    Optional<RefreshToken> findByUser(User user);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user = :user")
    void revokeAllByUser(User user);

    boolean existsByToken(String token);

    @Modifying
    @Transactional
    @Query("""
    UPDATE RefreshToken r SET r.revoked = true
    WHERE r.user = :user AND r.tokenFamilyId = :familyId
    """)
    void revokeFamily(@Param("user") User user, @Param("familyId") UUID familyId);
}
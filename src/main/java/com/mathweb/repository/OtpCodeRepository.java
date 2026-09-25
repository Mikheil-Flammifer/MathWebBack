package com.mathweb.repository;

import com.mathweb.entity.OtpCode;
import com.mathweb.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findByUserAndCodeAndUsedFalse(User user, String code);

    Optional<OtpCode> findTopByUserAndPurposeOrderByCreatedAtDesc(User user, String purpose);

    @Modifying
    @Query("UPDATE OtpCode o SET o.used = true WHERE o.user = :user AND o.purpose = :purpose")
    void invalidateAllByUserAndPurpose(User user, String purpose);

    @Modifying
    @Query("DELETE FROM OtpCode o WHERE o.expiresAt < :now")
    void deleteExpiredCodes(LocalDateTime now);
}
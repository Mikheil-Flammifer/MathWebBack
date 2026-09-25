package com.mathweb.repository;

import com.mathweb.entity.ProblemAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProblemAttemptRepository extends JpaRepository<ProblemAttempt, Long> {

    Optional<ProblemAttempt> findByUserIdAndProblemId(Long userId, Long problemId);

    List<ProblemAttempt> findByUserId(Long userId);

    @Query("SELECT pa FROM ProblemAttempt pa WHERE pa.user.id = :userId AND pa.problem.quest.id = :questId")
    List<ProblemAttempt> findByUserIdAndQuestId(Long userId, Long questId);

    @Query("SELECT COUNT(pa) FROM ProblemAttempt pa WHERE pa.user.id = :userId AND pa.solved = true")
    long countSolvedByUserId(Long userId);

    @Query("SELECT SUM(pa.xpEarned) FROM ProblemAttempt pa WHERE pa.user.id = :userId")
    Long sumXpEarnedByUserId(Long userId);
}
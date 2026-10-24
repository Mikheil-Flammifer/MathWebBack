package com.mathweb.repository;

import com.mathweb.entity.ProblemAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProblemAttemptRepository extends JpaRepository<ProblemAttempt, Long> {

    Optional<ProblemAttempt> findByUserIdAndProblemId(Long userId, Long problemId);

    List<ProblemAttempt> findByUserId(Long userId);

    List<ProblemAttempt> findByUserIdAndProblemIdIn(Long userId, Collection<Long> problemIds);

    @Query("SELECT pa FROM ProblemAttempt pa WHERE pa.user.id = :userId AND pa.problem.quest.id = :questId")
    List<ProblemAttempt> findByUserIdAndQuestId(Long userId, Long questId);

    @Query("SELECT COUNT(pa) FROM ProblemAttempt pa WHERE pa.user.id = :userId AND pa.solved = true")
    long countSolvedByUserId(Long userId);

    @Query("SELECT SUM(pa.xpEarned) FROM ProblemAttempt pa WHERE pa.user.id = :userId")
    Long sumXpEarnedByUserId(Long userId);

    @Query("SELECT pa.problem.id FROM ProblemAttempt pa " +
            "WHERE pa.user.id = :userId AND pa.problem.quest.id = :questId AND pa.solved = true")
    List<Long> findSolvedProblemIds(Long userId, Long questId);

    @Query("SELECT pa FROM ProblemAttempt pa JOIN FETCH pa.problem p " +
            "WHERE pa.user.id = :userId AND p.category IS NOT NULL AND p.quest.published = true")
    List<ProblemAttempt> findForProgress(Long userId);
}
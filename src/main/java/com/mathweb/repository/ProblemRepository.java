package com.mathweb.repository;

import com.mathweb.entity.Problem;
import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.ProblemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProblemRepository extends JpaRepository<Problem, Long> {

    List<Problem> findByQuestIdOrderByOrderIndexAsc(Long questId);

    List<Problem> findByDifficultyLevel(DifficultyLevel level);

    List<Problem> findByProblemType(ProblemType type);

    @Query("SELECT COUNT(p) FROM Problem p WHERE p.quest.id = :questId")
    int countByQuestId(Long questId);

    @Query("SELECT p FROM Problem p WHERE p.quest.id = :questId AND p.problemType = :type ORDER BY p.orderIndex ASC")
    List<Problem> findByQuestIdAndType(Long questId, ProblemType type);
}
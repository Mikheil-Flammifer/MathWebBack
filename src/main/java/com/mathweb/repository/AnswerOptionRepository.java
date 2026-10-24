package com.mathweb.repository;

import com.mathweb.entity.AnswerOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AnswerOptionRepository extends JpaRepository<AnswerOption, Long> {

    List<AnswerOption> findByProblemIdOrderByOrderIndexAsc(Long problemId);

    List<AnswerOption> findByProblemIdInOrderByOrderIndexAsc(Collection<Long> problemIds);

    void deleteByProblemId(Long problemId);
}
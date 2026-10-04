package com.mathweb.repository;

import com.mathweb.entity.ProblemLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProblemLinkRepository extends JpaRepository<ProblemLink, Long> {

    List<ProblemLink> findByQuestId(Long questId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ProblemLink l WHERE l.questId = :questId")
    void deleteByQuestId(Long questId);
}
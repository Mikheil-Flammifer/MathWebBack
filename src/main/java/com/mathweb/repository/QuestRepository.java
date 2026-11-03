package com.mathweb.repository;

import com.mathweb.entity.Quest;
import com.mathweb.enums.DifficultyLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestRepository extends JpaRepository<Quest, Long> {

    List<Quest> findByPublishedTrueOrderByDifficultyLevelAsc();

    List<Quest> findByDifficultyLevelAndPublishedTrue(DifficultyLevel level);

    // Quests with no prerequisites (entry points of the DAG)
    @Query("SELECT q FROM Quest q WHERE q.published = true AND q.prerequisites IS EMPTY")
    List<Quest> findRootQuests();

    // Quests the user has NOT started yet but prerequisites are met
    @Query("""
            SELECT q FROM Quest q
            WHERE q.published = true
            AND q.id NOT IN (
                SELECT uqp.quest.id FROM UserQuestProgress uqp WHERE uqp.user.id = :userId
            )
            """)
    List<Quest> findNotStartedByUser(Long userId);

    // Students: published quests only
    @Query("""
       select q from Quest q
       where q.published = true
         and (:level is null or q.difficultyLevel = :level)
         and lower(q.title) like lower(concat('%', :keyword, '%'))
       """)
    Page<Quest> findPublishedFiltered(@Param("level") DifficultyLevel level,
                                      @Param("keyword") String keyword,
                                      Pageable pageable);

    // Admin: published = null means both published and drafts
    @Query("""
       select q from Quest q
       where (:published is null or q.published = :published)
         and (:level is null or q.difficultyLevel = :level)
         and lower(q.title) like lower(concat('%', :keyword, '%'))
       """)
    Page<Quest> findForAdmin(@Param("published") Boolean published,
                             @Param("level") DifficultyLevel level,
                             @Param("keyword") String keyword,
                             Pageable pageable);

    List<Quest> findAllByOrderByDifficultyLevelAscIdAsc();

    boolean existsByTitleAndPublishedTrue(String title);
}
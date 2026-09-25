package com.mathweb.repository;

import com.mathweb.entity.UserQuestProgress;
import com.mathweb.enums.QuestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserQuestProgressRepository extends JpaRepository<UserQuestProgress, Long> {

    Optional<UserQuestProgress> findByUserIdAndQuestId(Long userId, Long questId);

    List<UserQuestProgress> findByUserId(Long userId);

    List<UserQuestProgress> findByUserIdAndStatus(Long userId, QuestStatus status);

    @Query("SELECT uqp FROM UserQuestProgress uqp WHERE uqp.user.id = :userId AND uqp.status = 'COMPLETED'")
    List<UserQuestProgress> findCompletedByUserId(Long userId);

    @Query("SELECT uqp.quest.id FROM UserQuestProgress uqp WHERE uqp.user.id = :userId AND uqp.status = 'COMPLETED'")
    List<Long> findCompletedQuestIdsByUserId(Long userId);

    boolean existsByUserIdAndQuestId(Long userId, Long questId);
}
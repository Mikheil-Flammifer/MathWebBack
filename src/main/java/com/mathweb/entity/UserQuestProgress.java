package com.mathweb.entity;

import com.mathweb.enums.QuestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_quest_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "quest_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserQuestProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quest_id", nullable = false)
    private Quest quest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private QuestStatus status = QuestStatus.LOCKED;

    @Column(name = "problems_solved")
    @Builder.Default
    private Integer problemsSolved = 0;

    @Column(name = "total_problems")
    private Integer totalProblems;

    @Column(name = "xp_earned")
    @Builder.Default
    private Integer xpEarned = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // ===== HELPERS =====

    public boolean isCompleted() {
        return status == QuestStatus.COMPLETED;
    }

    public double getProgressPercentage() {
        if (totalProblems == null || totalProblems == 0) return 0.0;
        return (double) problemsSolved / totalProblems * 100.0;
    }
}
package com.mathweb.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "problem_attempts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "problem_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(name = "attempts_used", nullable = false)
    @Builder.Default
    private Integer attemptsUsed = 0;

    @Column(name = "solved", nullable = false)
    @Builder.Default
    private Boolean solved = false;

    @Column(name = "answer_revealed", nullable = false)
    @Builder.Default
    private Boolean answerRevealed = false;

    @Column(name = "last_answer_given", columnDefinition = "TEXT")
    private String lastAnswerGiven;

    @Column(name = "xp_earned")
    @Builder.Default
    private Integer xpEarned = 0;

    // ===== HELPERS =====

    public boolean canAttempt() {
        return !solved && attemptsUsed < problem.getMaxAttempts();
    }

    public boolean isExhausted() {
        return !solved && attemptsUsed >= problem.getMaxAttempts();
    }
}
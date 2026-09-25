package com.mathweb.entity;

import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.ProblemType;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "problems")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Problem extends BaseEntity {

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "question_image_path")
    private String questionImagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "problem_type", nullable = false, length = 20)
    private ProblemType problemType;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", nullable = false, length = 40)
    private DifficultyLevel difficultyLevel;

    @Column(name = "correct_answer", columnDefinition = "TEXT")
    private String correctAnswer;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "explanation_image_path")
    private String explanationImagePath;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 3;

    @Column(name = "xp_reward")
    @Builder.Default
    private Integer xpReward = 10;

    // ===== RELATIONS =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quest_id", nullable = false)
    private Quest quest;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AnswerOption> answerOptions = new ArrayList<>();

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProblemAttempt> attempts = new ArrayList<>();
}
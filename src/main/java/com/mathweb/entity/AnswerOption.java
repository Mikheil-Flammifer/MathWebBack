package com.mathweb.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "answer_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnswerOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(name = "option_text", nullable = false, columnDefinition = "TEXT")
    private String optionText;

    @Column(name = "option_image_path")
    private String optionImagePath;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;
}
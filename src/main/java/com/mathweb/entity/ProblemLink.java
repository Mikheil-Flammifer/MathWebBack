package com.mathweb.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "problem_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemLink extends BaseEntity {

    @Column(name = "quest_id", nullable = false)
    private Long questId;

    @Column(name = "problem_a_id", nullable = false)
    private Long problemAId;

    @Column(name = "problem_b_id", nullable = false)
    private Long problemBId;
}
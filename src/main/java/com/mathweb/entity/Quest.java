package com.mathweb.entity;

import com.mathweb.enums.DifficultyLevel;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quest extends BaseEntity {

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "icon_url")
    private String iconUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", nullable = false, length = 40)
    private DifficultyLevel difficultyLevel;

    @Column(name = "position_x")
    private Integer positionX;

    @Column(name = "position_y")
    private Integer positionY;

    @Column(name = "published", nullable = false)
    @Builder.Default
    private Boolean published = false;

    @Column(name = "xp_reward")
    @Builder.Default
    private Integer xpReward = 0;

    // ===== DAG GRAPH =====

    @ManyToMany
    @JoinTable(
            name = "quest_prerequisites",
            joinColumns = @JoinColumn(name = "quest_id"),
            inverseJoinColumns = @JoinColumn(name = "prerequisite_id")
    )
    @Builder.Default
    private List<Quest> prerequisites = new ArrayList<>();

    @ManyToMany(mappedBy = "prerequisites")
    @Builder.Default
    private List<Quest> dependents = new ArrayList<>();

    // ===== RELATIONS =====

    @OneToMany(mappedBy = "quest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Problem> problems = new ArrayList<>();

    @OneToMany(mappedBy = "quest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Video> videos = new ArrayList<>();

    @OneToMany(mappedBy = "quest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserQuestProgress> userProgresses = new ArrayList<>();

    // ===== HELPERS =====

    public boolean isUnlocked(List<Long> completedQuestIds) {
        if (prerequisites.isEmpty()) return true;
        return prerequisites.stream()
                .allMatch(p -> completedQuestIds.contains(p.getId()));
    }
}
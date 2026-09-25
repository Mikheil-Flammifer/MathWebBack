package com.mathweb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment extends BaseEntity {

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "depth", nullable = false)
    @Builder.Default
    private Integer depth = 0;

    @Column(name = "edited", nullable = false)
    @Builder.Default
    private Boolean edited = false;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "upvotes")
    @Builder.Default
    private Integer upvotes = 0;

    // ===== RELATIONS =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Comment> replies = new ArrayList<>();

    // ===== HELPERS =====

    public boolean isTopLevel() {
        return parent == null;
    }

    public String getDisplayContent() {
        return deleted ? "[deleted]" : content;
    }
}
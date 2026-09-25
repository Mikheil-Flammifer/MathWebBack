package com.mathweb.entity;

import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.VideoStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "videos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Video extends BaseEntity {

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "thumbnail_path")
    private String thumbnailPath;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "mime_type", length = 50)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private VideoStatus status = VideoStatus.UPLOADING;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", length = 40)
    private DifficultyLevel difficultyLevel;

    @Column(name = "view_count")
    @Builder.Default
    private Long viewCount = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quest_id")
    private Quest quest;

    // ===== RELATIONS =====

    @OneToMany(mappedBy = "video", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();
}
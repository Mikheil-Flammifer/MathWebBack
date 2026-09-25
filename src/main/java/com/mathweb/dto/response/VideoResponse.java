package com.mathweb.dto.response;

import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.VideoStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoResponse {

    private Long id;
    private String title;
    private String description;
    private String filePath;
    private String thumbnailPath;
    private Long durationSeconds;
    private Long fileSizeBytes;
    private VideoStatus status;
    private DifficultyLevel difficultyLevel;
    private Long viewCount;
    private Long questId;
    private String questTitle;
    private UserResponse uploadedBy;
    private long commentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

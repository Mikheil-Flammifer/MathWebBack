package com.mathweb.dto.request;

import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.VideoStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateVideoRequest {

    @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
    private String title;

    @Size(max = 5000, message = "Description cannot exceed 5000 characters")
    private String description;

    private DifficultyLevel difficultyLevel;

    private VideoStatus status;

    private Long questId;
}
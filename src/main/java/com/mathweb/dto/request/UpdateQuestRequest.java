package com.mathweb.dto.request;

import com.mathweb.enums.DifficultyLevel;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateQuestRequest {

    @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
    private String title;                 // null = unchanged

    @Size(max = 5000, message = "Description cannot exceed 5000 characters")
    private String description;           // null = unchanged

    private DifficultyLevel difficultyLevel;  // null = unchanged
    private Integer positionX;
    private Integer positionY;
    private Integer xpReward;

    private List<Long> prerequisiteIds;   // null = unchanged, [] = clear
}
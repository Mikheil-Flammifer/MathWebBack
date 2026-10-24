package com.mathweb.dto.request;

import com.mathweb.enums.DifficultyLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @Min(value = -100000, message = "Position is out of range")
    @Max(value = 100000, message = "Position is out of range")
    private Integer positionX;

    @Min(value = -100000, message = "Position is out of range")
    @Max(value = 100000, message = "Position is out of range")
    private Integer positionY;

    @Min(value = 0, message = "XP reward cannot be negative")
    @Max(value = 10000, message = "XP reward cannot be more than 10000")
    private Integer xpReward;

    @Size(max = 50, message = "A quest can have at most 50 prerequisites")
    private List<Long> prerequisiteIds;   // null = unchanged, [] = clear
}
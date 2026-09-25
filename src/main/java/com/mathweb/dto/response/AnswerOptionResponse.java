package com.mathweb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerOptionResponse {

    private Long id;
    private String optionText;
    private String optionImagePath;
    private Integer orderIndex;
    // isCorrect is intentionally NOT included here
    // It is only revealed after attempts are exhausted
}
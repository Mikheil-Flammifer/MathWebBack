package com.mathweb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicProgressResponse {
    private Long categoryId;
    private String name;
    private String slug;
    private String icon;
    private String color;
    private Integer totalProblems;
    private Integer solved;
    private Integer firstTrySolved;
    private Double percent;                       // 0..100, one decimal
    private String level;                         // NOT_STARTED, LEARNING, MASTERED
    private List<TopicProgressResponse> subtopics; // empty for subcategories
}
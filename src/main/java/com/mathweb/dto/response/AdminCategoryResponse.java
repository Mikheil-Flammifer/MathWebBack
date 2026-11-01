package com.mathweb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCategoryResponse {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private String icon;
    private String color;
    private Integer orderIndex;
    private Long parentId;

    /** Videos and problems assigned directly to this category. */
    private long videoCount;
    private long problemCount;

    @Builder.Default
    private List<AdminCategoryResponse> children = new ArrayList<>();
}
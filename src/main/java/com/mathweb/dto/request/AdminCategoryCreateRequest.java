package com.mathweb.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminCategoryCreateRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    /** Optional. Generated from the name when empty. Lowercase letters, digits and hyphens. */
    @Size(max = 100, message = "Slug must be at most 100 characters")
    @Pattern(regexp = "^([a-z0-9]+(?:-[a-z0-9]+)*)?$",
            message = "Slug may contain lowercase letters, digits and single hyphens only")
    private String slug;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @Size(max = 50, message = "Icon must be at most 50 characters")
    private String icon;

    @Pattern(regexp = "^(#[0-9a-fA-F]{6})?$", message = "Color must look like #6366f1")
    private String color;

    /** Optional. Appended after the last sibling when empty. */
    @Min(0)
    @Max(100000)
    private Integer orderIndex;

    /** Optional. Must be a main category. Empty means this is a main category. */
    private Long parentId;
}
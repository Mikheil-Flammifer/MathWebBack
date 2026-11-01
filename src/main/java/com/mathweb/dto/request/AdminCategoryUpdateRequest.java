package com.mathweb.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Partial update. A missing (null) field means "leave unchanged".
 * For description, icon and color an empty string clears the value.
 * To make a subcategory a main category, send clearParent = true.
 */
@Data
public class AdminCategoryUpdateRequest {

    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

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

    @Min(0)
    @Max(100000)
    private Integer orderIndex;

    private Long parentId;

    /** Wins over parentId. */
    private Boolean clearParent;
}
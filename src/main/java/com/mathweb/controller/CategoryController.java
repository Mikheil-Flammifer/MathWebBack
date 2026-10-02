package com.mathweb.controller;

import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.CategoryResponse;
import com.mathweb.entity.Category;
import com.mathweb.repository.CategoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getTree() {
        List<Category> all = categoryRepository.findAllByOrderByOrderIndexAsc();

        Map<Long, List<Category>> childrenByParent = new HashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category c : all) {
            if (c.getParent() == null) {
                roots.add(c);
            } else {
                childrenByParent
                        .computeIfAbsent(c.getParent().getId(), k -> new ArrayList<>())
                        .add(c);
            }
        }

        List<CategoryResponse> tree = roots.stream()
                .map(r -> toResponse(r, childrenByParent))
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Categories retrieved", tree));
    }

    private CategoryResponse toResponse(Category c, Map<Long, List<Category>> childrenByParent) {
        return CategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .slug(c.getSlug())
                .icon(c.getIcon())
                .color(c.getColor())
                .parentId(c.getParent() != null ? c.getParent().getId() : null)
                .children(childrenByParent.getOrDefault(c.getId(), List.of()).stream()
                        .map(ch -> toResponse(ch, childrenByParent))
                        .toList())
                .build();
    }
}
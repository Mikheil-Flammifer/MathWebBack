package com.mathweb.controller;

import com.mathweb.dto.request.AdminCategoryCreateRequest;
import com.mathweb.dto.request.AdminCategoryUpdateRequest;
import com.mathweb.dto.response.AdminCategoryResponse;
import com.mathweb.dto.response.ApiResponse;
import com.mathweb.service.impl.AdminCategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {

    private final AdminCategoryService categoryService;

    public AdminCategoryController(AdminCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminCategoryResponse>>> getTree() {
        return ResponseEntity.ok(
                ApiResponse.success("Categories retrieved", categoryService.getTree()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminCategoryResponse>> create(
            @Valid @RequestBody AdminCategoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created", categoryService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminCategoryResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody AdminCategoryUpdateRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Category updated", categoryService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted"));
    }
}
package com.mathweb.repository;

import com.mathweb.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Used by GET /api/categories to build the tree
    List<Category> findAllByOrderByOrderIndexAsc();

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    // Main categories (Algebra, Geometry, ...)
    List<Category> findByParentIsNullOrderByOrderIndexAsc();

    // Subcategories of one main category
    List<Category> findByParentIdOrderByOrderIndexAsc(Long parentId);

    // For the admin delete rule: a category with children can't be deleted
    boolean existsByParentId(Long parentId);
}
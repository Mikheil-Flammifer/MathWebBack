package com.mathweb.service.impl;

import com.mathweb.dto.request.AdminCategoryCreateRequest;
import com.mathweb.dto.request.AdminCategoryUpdateRequest;
import com.mathweb.dto.response.AdminCategoryResponse;
import com.mathweb.entity.Category;
import com.mathweb.exception.BadRequestException;
import com.mathweb.exception.CategoryConflictException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Admin category management. Two levels only: main categories and their subcategories.
 */
@Service
public class AdminCategoryService {

    private static final Logger log = LoggerFactory.getLogger(AdminCategoryService.class);

    private final CategoryRepository categoryRepository;
    private final JdbcTemplate jdbcTemplate;

    public AdminCategoryService(CategoryRepository categoryRepository, JdbcTemplate jdbcTemplate) {
        this.categoryRepository = categoryRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ===== READ =====

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> getTree() {
        List<Category> all = new ArrayList<>(categoryRepository.findAllByOrderByOrderIndexAsc());
        all.sort(Comparator.comparing(Category::getOrderIndex).thenComparing(Category::getId));

        Map<Long, Long> videoCounts = countByCategory("videos");
        Map<Long, Long> problemCounts = countByCategory("problems");

        Map<Long, AdminCategoryResponse> nodes = new LinkedHashMap<>();
        for (Category c : all) {
            nodes.put(c.getId(), toResponse(c,
                    videoCounts.getOrDefault(c.getId(), 0L),
                    problemCounts.getOrDefault(c.getId(), 0L)));
        }

        List<AdminCategoryResponse> roots = new ArrayList<>();
        for (Category c : all) {
            AdminCategoryResponse node = nodes.get(c.getId());
            AdminCategoryResponse parentNode =
                    c.getParent() == null ? null : nodes.get(c.getParent().getId());
            if (parentNode != null) {
                parentNode.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    // ===== CREATE =====

    @Transactional
    public AdminCategoryResponse create(AdminCategoryCreateRequest req) {
        String name = req.getName().trim();
        Category parent = resolveParent(req.getParentId());

        String slug = resolveSlug(req.getSlug(), name);
        if (categoryRepository.existsBySlug(slug)) {
            throw new CategoryConflictException(
                    "Slug '" + slug + "' is already in use. Send a different slug.");
        }
        assertNameFree(name, idOf(parent), null);

        int order = req.getOrderIndex() != null ? req.getOrderIndex() : nextOrder(parent);

        Category category = Category.builder()
                .name(name)
                .slug(slug)
                .description(blankToNull(req.getDescription()))
                .icon(blankToNull(req.getIcon()))
                .color(blankToNull(req.getColor()))
                .orderIndex(order)
                .parent(parent)
                .build();
        category = categoryRepository.save(category);

        log.info("Category created: {} (id {})", category.getName(), category.getId());
        return toResponse(category, 0L, 0L);
    }

    // ===== UPDATE =====

    @Transactional
    public AdminCategoryResponse update(Long id, AdminCategoryUpdateRequest req) {
        Category category = findOrThrow(id);

        // Parent
        Category newParent = category.getParent();
        if (Boolean.TRUE.equals(req.getClearParent())) {
            newParent = null;
        } else if (req.getParentId() != null) {
            if (req.getParentId().equals(id)) {
                throw new BadRequestException("A category cannot be its own parent");
            }
            newParent = resolveParent(req.getParentId());
        }
        boolean parentChanged = !Objects.equals(idOf(newParent), idOf(category.getParent()));
        if (parentChanged && newParent != null && categoryRepository.existsByParentId(id)) {
            throw new BadRequestException(
                    "A category that has subcategories cannot become a subcategory");
        }

        // Name
        String name = category.getName();
        if (req.getName() != null) {
            name = req.getName().trim();
            if (name.isEmpty()) {
                throw new BadRequestException("Name must not be blank");
            }
        }
        boolean nameChanged = !name.equals(category.getName());
        if (nameChanged || parentChanged) {
            assertNameFree(name, idOf(newParent), id);
        }

        // Slug (kept as is unless a different one is sent)
        if (req.getSlug() != null && !req.getSlug().isBlank()) {
            String slug = req.getSlug().trim();
            if (!slug.equals(category.getSlug())) {
                if (categoryRepository.existsBySlug(slug)) {
                    throw new CategoryConflictException("Slug '" + slug + "' is already in use");
                }
                category.setSlug(slug);
            }
        }

        category.setName(name);
        category.setParent(newParent);
        if (req.getDescription() != null) category.setDescription(blankToNull(req.getDescription()));
        if (req.getIcon() != null) category.setIcon(blankToNull(req.getIcon()));
        if (req.getColor() != null) category.setColor(blankToNull(req.getColor()));
        if (req.getOrderIndex() != null) category.setOrderIndex(req.getOrderIndex());

        category = categoryRepository.save(category);
        log.info("Category updated: {} (id {})", category.getName(), category.getId());
        return toResponse(category, countIn("videos", id), countIn("problems", id));
    }

    // ===== DELETE =====

    @Transactional
    public void delete(Long id) {
        Category category = findOrThrow(id);

        long children = categoryRepository.countByParentId(id);
        long videos = countIn("videos", id);
        long problems = countIn("problems", id);

        // The database would silently set category_id to NULL on videos and problems,
        // so refuse here and make the admin move them first.
        if (children + videos + problems > 0) {
            throw new CategoryConflictException(String.format(
                    "Category is in use: %d subcategories, %d videos, %d problems. "
                            + "Move or delete them first.", children, videos, problems));
        }

        categoryRepository.delete(category);
        log.info("Category deleted: {} (id {})", category.getName(), id);
    }

    // ===== HELPERS =====

    private Category findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    /** A parent must exist and must itself be a main category (two levels only). */
    private Category resolveParent(Long parentId) {
        if (parentId == null) return null;
        Category parent = findOrThrow(parentId);
        if (parent.getParent() != null) {
            throw new BadRequestException("A subcategory cannot be used as a parent");
        }
        return parent;
    }

    private void assertNameFree(String name, Long parentId, Long excludeId) {
        boolean exists;
        if (parentId == null) {
            exists = excludeId == null
                    ? categoryRepository.existsByParentIsNullAndNameIgnoreCase(name)
                    : categoryRepository.existsByParentIsNullAndNameIgnoreCaseAndIdNot(name, excludeId);
        } else {
            exists = excludeId == null
                    ? categoryRepository.existsByParentIdAndNameIgnoreCase(parentId, name)
                    : categoryRepository.existsByParentIdAndNameIgnoreCaseAndIdNot(parentId, name, excludeId);
        }
        if (exists) {
            throw new CategoryConflictException(
                    "A category named '" + name + "' already exists here");
        }
    }

    private int nextOrder(Category parent) {
        List<Category> siblings = parent == null
                ? categoryRepository.findByParentIsNullOrderByOrderIndexAsc()
                : categoryRepository.findByParentIdOrderByOrderIndexAsc(parent.getId());
        return siblings.isEmpty() ? 1 : siblings.get(siblings.size() - 1).getOrderIndex() + 1;
    }

    private String resolveSlug(String requested, String name) {
        String slug = (requested == null || requested.isBlank())
                ? slugify(name)
                : requested.trim();
        if (slug.isEmpty()) {
            throw new BadRequestException(
                    "A slug is required for this name (use lowercase letters, digits and hyphens)");
        }
        return slug;
    }

    private static String slugify(String input) {
        String n = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        n = n.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (n.length() > 100) {
            n = n.substring(0, 100).replaceAll("-+$", "");
        }
        return n;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static Long idOf(Category c) {
        return c == null ? null : c.getId();
    }

    private AdminCategoryResponse toResponse(Category c, long videoCount, long problemCount) {
        return AdminCategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .slug(c.getSlug())
                .description(c.getDescription())
                .icon(c.getIcon())
                .color(c.getColor())
                .orderIndex(c.getOrderIndex())
                .parentId(idOf(c.getParent()))
                .videoCount(videoCount)
                .problemCount(problemCount)
                .build();
    }

    // Counts use SQL on the category_id columns so this does not depend on how
    // the Problem entity maps its category. The table names are fixed strings.
    private Map<Long, Long> countByCategory(String table) {
        Map<Long, Long> result = new HashMap<>();
        jdbcTemplate.query(
                "select category_id, count(*) from " + table
                        + " where category_id is not null group by category_id",
                (RowCallbackHandler) rs -> result.put(rs.getLong(1), rs.getLong(2)));
        return result;
    }

    private long countIn(String table, Long categoryId) {
        Long n = jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where category_id = ?",
                Long.class, categoryId);
        return n == null ? 0L : n;
    }
}
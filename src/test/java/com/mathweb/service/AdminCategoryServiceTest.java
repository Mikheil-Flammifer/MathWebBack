package com.mathweb.service;

import com.mathweb.dto.request.AdminCategoryCreateRequest;
import com.mathweb.dto.request.AdminCategoryUpdateRequest;
import com.mathweb.dto.response.AdminCategoryResponse;
import com.mathweb.entity.Category;
import com.mathweb.exception.BadRequestException;
import com.mathweb.exception.CategoryConflictException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.CategoryRepository;
import com.mathweb.service.impl.AdminCategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test of the category rules. No Spring context, no database, no Docker.
 */
@ExtendWith(MockitoExtension.class)
class AdminCategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AdminCategoryService service;

    // ===== helpers =====

    private static Category category(Long id, String name, String slug, Category parent) {
        Category c = Category.builder()
                .name(name)
                .slug(slug)
                .orderIndex(1)
                .parent(parent)
                .build();
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private static AdminCategoryCreateRequest createRequest(String name) {
        AdminCategoryCreateRequest r = new AdminCategoryCreateRequest();
        r.setName(name);
        return r;
    }

    private void stubSaveReturnsArgument() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Category savedCategory() {
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        return captor.getValue();
    }

    // ===== create =====

    @Test
    void create_mainCategory_trimsNameAndGeneratesSlug() {
        stubSaveReturnsArgument();

        AdminCategoryResponse response = service.create(createRequest("  Number Theory  "));

        Category saved = savedCategory();
        assertThat(saved.getName()).isEqualTo("Number Theory");
        assertThat(saved.getSlug()).isEqualTo("number-theory");
        assertThat(saved.getOrderIndex()).isEqualTo(1);
        assertThat(saved.getParent()).isNull();
        assertThat(response.getSlug()).isEqualTo("number-theory");
        assertThat(response.getVideoCount()).isZero();
    }

    @Test
    void create_slugIsAsciiAndHyphenated() {
        stubSaveReturnsArgument();

        service.create(createRequest("Café & Bar!"));

        assertThat(savedCategory().getSlug()).isEqualTo("cafe-bar");
    }

    @Test
    void create_withExplicitSlug_usesIt() {
        stubSaveReturnsArgument();
        AdminCategoryCreateRequest request = createRequest("Algebra");
        request.setSlug("alg");

        service.create(request);

        assertThat(savedCategory().getSlug()).isEqualTo("alg");
    }

    @Test
    void create_georgianNameWithoutSlug_isRejected() {
        assertThatThrownBy(() -> service.create(createRequest("ალგებრა")))
                .isInstanceOf(BadRequestException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_slugAlreadyUsed_isConflict() {
        when(categoryRepository.existsBySlug("algebra")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest("Algebra")))
                .isInstanceOf(CategoryConflictException.class)
                .hasMessageContaining("algebra");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_sameNameUnderSameParent_isConflict() {
        Category main = category(1L, "Geometry", "geometry", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(main));
        when(categoryRepository.existsByParentIdAndNameIgnoreCase(1L, "Triangles")).thenReturn(true);
        AdminCategoryCreateRequest request = createRequest("Triangles");
        request.setParentId(1L);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(CategoryConflictException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_parentIsSubcategory_isRejected() {
        Category main = category(1L, "Geometry", "geometry", null);
        Category sub = category(2L, "Triangles", "triangles", main);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(sub));
        AdminCategoryCreateRequest request = createRequest("Right Triangles");
        request.setParentId(2L);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BadRequestException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_unknownParent_isNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());
        AdminCategoryCreateRequest request = createRequest("Anything");
        request.setParentId(99L);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_withParent_isAppendedAfterLastSibling() {
        stubSaveReturnsArgument();
        Category main = category(1L, "Geometry", "geometry", null);
        Category lastSibling = category(5L, "Circles", "circles", main);
        lastSibling.setOrderIndex(3);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(main));
        when(categoryRepository.findByParentIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(lastSibling));
        AdminCategoryCreateRequest request = createRequest("Polygons");
        request.setParentId(1L);

        service.create(request);

        Category saved = savedCategory();
        assertThat(saved.getOrderIndex()).isEqualTo(4);
        assertThat(saved.getParent()).isSameAs(main);
    }

    // ===== update =====

    @Test
    void update_categoryCannotBeItsOwnParent() {
        Category main = category(5L, "Algebra", "algebra", null);
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(main));
        AdminCategoryUpdateRequest request = new AdminCategoryUpdateRequest();
        request.setParentId(5L);

        assertThatThrownBy(() -> service.update(5L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("own parent");
    }

    @Test
    void update_categoryWithChildren_cannotBecomeSubcategory() {
        Category geometry = category(1L, "Geometry", "geometry", null);
        Category algebra = category(2L, "Algebra", "algebra", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(geometry));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(algebra));
        when(categoryRepository.existsByParentId(1L)).thenReturn(true);
        AdminCategoryUpdateRequest request = new AdminCategoryUpdateRequest();
        request.setParentId(2L);

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(BadRequestException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void update_clearParent_makesItAMainCategory() {
        stubSaveReturnsArgument();
        Category main = category(1L, "Geometry", "geometry", null);
        Category sub = category(2L, "Triangles", "triangles", main);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(sub));
        AdminCategoryUpdateRequest request = new AdminCategoryUpdateRequest();
        request.setClearParent(true);

        AdminCategoryResponse response = service.update(2L, request);

        assertThat(sub.getParent()).isNull();
        assertThat(response.getParentId()).isNull();
    }

    @Test
    void update_renameToExistingSiblingName_isConflict() {
        Category main = category(1L, "Geometry", "geometry", null);
        Category sub = category(2L, "Triangles", "triangles", main);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(sub));
        when(categoryRepository.existsByParentIdAndNameIgnoreCaseAndIdNot(1L, "Circles", 2L)).thenReturn(true);
        AdminCategoryUpdateRequest request = new AdminCategoryUpdateRequest();
        request.setName("Circles");

        assertThatThrownBy(() -> service.update(2L, request))
                .isInstanceOf(CategoryConflictException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void update_rename_keepsTheExistingSlug() {
        stubSaveReturnsArgument();
        Category main = category(1L, "Geometry", "geometry", null);
        Category sub = category(2L, "Triangles", "triangles", main);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(sub));
        AdminCategoryUpdateRequest request = new AdminCategoryUpdateRequest();
        request.setName("Triangles and More");

        service.update(2L, request);

        assertThat(sub.getName()).isEqualTo("Triangles and More");
        assertThat(sub.getSlug()).isEqualTo("triangles");
    }

    @Test
    void update_slugOfAnotherCategory_isConflict() {
        Category sub = category(2L, "Triangles", "triangles", null);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(sub));
        when(categoryRepository.existsBySlug("circles")).thenReturn(true);
        AdminCategoryUpdateRequest request = new AdminCategoryUpdateRequest();
        request.setSlug("circles");

        assertThatThrownBy(() -> service.update(2L, request))
                .isInstanceOf(CategoryConflictException.class);

        verify(categoryRepository, never()).save(any());
    }

    // ===== delete =====

    @Test
    void delete_categoryInUse_isRefusedWithCounts() {
        Category category = category(7L, "Algebra", "algebra", null);
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(category));
        when(categoryRepository.countByParentId(7L)).thenReturn(2L);
        when(jdbcTemplate.queryForObject(contains("from videos"), eq(Long.class), eq(7L))).thenReturn(5L);
        when(jdbcTemplate.queryForObject(contains("from problems"), eq(Long.class), eq(7L))).thenReturn(0L);

        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(CategoryConflictException.class)
                .hasMessageContaining("2 subcategories")
                .hasMessageContaining("5 videos");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void delete_emptyCategory_isDeleted() {
        Category category = category(8L, "Vectors", "vectors", null);
        when(categoryRepository.findById(8L)).thenReturn(Optional.of(category));

        service.delete(8L);

        verify(categoryRepository).delete(category);
    }

    @Test
    void delete_unknownCategory_isNotFound() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
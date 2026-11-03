package com.mathweb.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CategoryEntityTest {

    @Test
    void testNoArgsConstructor() {
        Category category = new Category();
        assertNull(category.getId());
        assertNull(category.getName());
        assertNull(category.getSlug());
        assertEquals(Integer.valueOf(0), category.getOrderIndex());
        assertNull(category.getParent());
    }

    @Test
    void testBuilder_WithParent() {
        Category parent = Category.builder().build();

        Category category = Category.builder()
                .name("Sub Category")
                .slug("sub-category")
                .orderIndex(5)
                .parent(parent)
                .build();

        assertEquals("Sub Category", category.getName());
        assertEquals("sub-category", category.getSlug());
        assertEquals(Integer.valueOf(5), category.getOrderIndex());
        assertEquals(parent, category.getParent());
    }

    @Test
    void testSettersAndGetters() {
        Category category = new Category();
        Category parent = new Category();

        category.setName("Math");
        category.setSlug("math");
        category.setDescription("Mathematics category");
        category.setIcon("math-icon");
        category.setColor("#0000FF");
        category.setOrderIndex(10);
        category.setParent(parent);

        assertEquals("Math", category.getName());
        assertEquals("math", category.getSlug());
        assertEquals("Mathematics category", category.getDescription());
        assertEquals("#0000FF", category.getColor());
        assertEquals(Integer.valueOf(10), category.getOrderIndex());
        assertEquals(parent, category.getParent());
    }
}
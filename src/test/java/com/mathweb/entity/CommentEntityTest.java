package com.mathweb.entity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommentEntityTest {

    @Test
    void testNoArgsConstructor() {
        Comment comment = new Comment();
        assertNull(comment.getId());
        assertNull(comment.getContent());
        assertEquals(Integer.valueOf(0), comment.getDepth());
        assertFalse(comment.getEdited());
        assertFalse(comment.getDeleted());
        assertEquals(Integer.valueOf(0), comment.getUpvotes());
        assertEquals(Integer.valueOf(0), comment.getDownvotes());
    }

    @Test
    void testIsTopLevel_NoParent() {
        Comment comment = Comment.builder()
                .parent(null)
                .build();
        assertTrue(comment.isTopLevel());
    }

    @Test
    void testIsTopLevel_WithParent() {
        Comment parent = Comment.builder().build();
        Comment comment = Comment.builder()
                .parent(parent)
                .build();
        assertFalse(comment.isTopLevel());
    }

    @Test
    void testGetDisplayContent_NotDeleted() {
        Comment comment = Comment.builder()
                .content("Hello World")
                .deleted(false)
                .build();
        assertEquals("Hello World", comment.getDisplayContent());
    }

    @Test
    void testGetDisplayContent_Deleted() {
        Comment comment = Comment.builder()
                .content("Hello World")
                .deleted(true)
                .build();
        assertEquals("[deleted]", comment.getDisplayContent());
    }
}
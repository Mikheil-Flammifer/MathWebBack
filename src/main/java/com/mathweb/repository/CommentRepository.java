package com.mathweb.repository;

import com.mathweb.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Top-level comments for a video (no parent)
    @Query("SELECT c FROM Comment c WHERE c.video.id = :videoId AND c.parent IS NULL ORDER BY c.createdAt DESC")
    Page<Comment> findTopLevelByVideoId(Long videoId, Pageable pageable);

    // Direct replies to a comment
    List<Comment> findByParentIdOrderByCreatedAtAsc(Long parentId);

    // All comments by a user
    Page<Comment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Count comments on a video (excluding deleted)
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.video.id = :videoId AND c.deleted = false")
    long countActiveByVideoId(Long videoId);

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.user.id = :userId AND c.deleted = false")
    long countActiveByUserId(Long userId);
}
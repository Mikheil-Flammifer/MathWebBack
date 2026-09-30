package com.mathweb.repository;

import com.mathweb.entity.CommentVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentVoteRepository extends JpaRepository<CommentVote, Long> {

    Optional<CommentVote> findByCommentIdAndUserId(Long commentId, Long userId);

    @Query("""
           select v from CommentVote v
           where v.user.id = :userId and v.comment.video.id = :videoId
           """)
    List<CommentVote> findByUserIdAndVideoId(@Param("userId") Long userId,
                                             @Param("videoId") Long videoId);
}
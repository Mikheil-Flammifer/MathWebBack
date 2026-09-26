package com.mathweb.service;

import com.mathweb.dto.request.CreateCommentRequest;
import com.mathweb.dto.request.UpdateCommentRequest;
import com.mathweb.dto.response.CommentResponse;
import com.mathweb.dto.response.PageResponse;

public interface CommentService {
    CommentResponse createComment(CreateCommentRequest request, Long userId);
    CommentResponse updateComment(Long commentId, UpdateCommentRequest request, Long userId);
    void deleteComment(Long commentId, Long userId);
    PageResponse<CommentResponse> getVideoComments(Long videoId, int page, int size);
    CommentResponse upvoteComment(Long commentId, Long userId);
}
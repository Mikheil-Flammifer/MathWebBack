package com.mathweb.controller;

import com.mathweb.dto.request.CreateCommentRequest;
import com.mathweb.dto.request.UpdateCommentRequest;
import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.CommentResponse;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/video/{videoId}")
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> getVideoComments(
            @PathVariable Long videoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<CommentResponse> comments =
                commentService.getVideoComments(videoId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Comments retrieved", comments));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        CommentResponse comment = commentService.createComment(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Comment created", comment));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCommentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        CommentResponse comment = commentService.updateComment(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Comment updated", comment));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        commentService.deleteComment(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Comment deleted"));
    }

    @PostMapping("/{id}/upvote")
    public ResponseEntity<ApiResponse<CommentResponse>> upvoteComment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        CommentResponse comment = commentService.upvoteComment(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Comment upvoted", comment));
    }
}
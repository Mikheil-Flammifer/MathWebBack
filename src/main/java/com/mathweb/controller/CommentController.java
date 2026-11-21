package com.mathweb.controller;

import com.mathweb.dto.request.CreateCommentRequest;
import com.mathweb.dto.request.UpdateCommentRequest;
import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.CommentResponse;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.CommentService;
import com.mathweb.service.impl.RateLimitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;
    private final RateLimitService rateLimitService;

    public CommentController(
            CommentService commentService,
            RateLimitService rateLimitService
    ) {
        this.commentService = commentService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/video/{videoId}")
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> getVideoComments(
            @PathVariable Long videoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long userId = principal != null ? principal.getId() : null;

        PageResponse<CommentResponse> comments =
                commentService.getVideoComments(videoId, page, size, userId);

        return ResponseEntity.ok(
                ApiResponse.success("Comments retrieved", comments)
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        rateLimitService.checkAccountRate(
                "comment",
                principal.getId(),
                15,
                "Too many comment requests. Please wait."
        );

        CommentResponse comment =
                commentService.createComment(request, principal.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Comment created", comment));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCommentRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        rateLimitService.checkAccountRate(
                "comment",
                principal.getId(),
                15,
                "Too many comment requests. Please wait."
        );

        CommentResponse comment =
                commentService.updateComment(id, request, principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success("Comment updated", comment)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        rateLimitService.checkAccountRate(
                "comment",
                principal.getId(),
                15,
                "Too many comment requests. Please wait."
        );

        commentService.deleteComment(id, principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success("Comment deleted")
        );
    }

    @PostMapping("/{id}/upvote")
    public ResponseEntity<ApiResponse<CommentResponse>> upvoteComment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        rateLimitService.checkAccountRate(
                "comment-vote",
                principal.getId(),
                30,
                "Too many vote requests. Please wait."
        );

        CommentResponse comment =
                commentService.upvoteComment(id, principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success("Vote updated", comment)
        );
    }

    @PostMapping("/{id}/downvote")
    public ResponseEntity<ApiResponse<CommentResponse>> downvoteComment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        rateLimitService.checkAccountRate(
                "comment-vote",
                principal.getId(),
                30,
                "Too many vote requests. Please wait."
        );

        CommentResponse comment =
                commentService.downvoteComment(id, principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success("Vote updated", comment)
        );
    }
}
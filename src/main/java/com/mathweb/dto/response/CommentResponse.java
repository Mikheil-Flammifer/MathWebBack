package com.mathweb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {

    private Long id;
    private String content;
    private Integer depth;
    private Boolean edited;
    private Boolean deleted;
    private Integer upvotes;
    private UserResponse user;
    private Long videoId;
    private Long parentId;
    private List<CommentResponse> replies;  // nested replies
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
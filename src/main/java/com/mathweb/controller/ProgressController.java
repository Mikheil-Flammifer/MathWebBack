package com.mathweb.controller;

import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.TopicProgressResponse;
import com.mathweb.dto.response.MistakeResponse;
import com.mathweb.dto.response.StreakResponse;
import org.springframework.web.bind.annotation.RequestParam;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.impl.ProgressService;
import com.mathweb.service.impl.StreakService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;
    private final StreakService streakService;

    public ProgressController(ProgressService progressService, StreakService streakService) {
        this.progressService = progressService;
        this.streakService = streakService;
    }

    @GetMapping("/topics")
    public ResponseEntity<ApiResponse<List<TopicProgressResponse>>> getTopics(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Topic progress retrieved",
                progressService.getTopicProgress(principal.getId())));
    }

    @GetMapping("/mistakes")
    public ResponseEntity<ApiResponse<List<MistakeResponse>>> getMistakes(
            @RequestParam(required = false) Long categoryId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Mistakes retrieved",
                progressService.getMistakes(principal.getId(), categoryId)));
    }

    @GetMapping("/streak")
    public ResponseEntity<ApiResponse<StreakResponse>> getStreak(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Streak retrieved",
                streakService.getStreak(principal.getId())));
    }
}
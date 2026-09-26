package com.mathweb.controller;

import com.mathweb.dto.request.CreateProblemRequest;
import com.mathweb.dto.request.SubmitAnswerRequest;
import com.mathweb.dto.response.AnswerResultResponse;
import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.ProblemResponse;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.ProblemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping("/quest/{questId}")
    public ResponseEntity<ApiResponse<List<ProblemResponse>>> getProblemsByQuest(
            @PathVariable Long questId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : null;
        List<ProblemResponse> problems = problemService.getProblemsByQuest(questId, userId);
        return ResponseEntity.ok(ApiResponse.success("Problems retrieved", problems));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProblemResponse>> getProblemById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : null;
        ProblemResponse problem = problemService.getProblemById(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Problem retrieved", problem));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<ProblemResponse>> createProblem(
            @Valid @RequestBody CreateProblemRequest request) {
        ProblemResponse problem = problemService.createProblem(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Problem created", problem));
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<AnswerResultResponse>> submitAnswer(
            @Valid @RequestBody SubmitAnswerRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        AnswerResultResponse result = problemService.submitAnswer(request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Answer submitted", result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProblem(@PathVariable Long id) {
        problemService.deleteProblem(id);
        return ResponseEntity.ok(ApiResponse.success("Problem deleted"));
    }
}
package com.mathweb.controller;

import com.mathweb.dto.request.ChangePasswordRequest;
import com.mathweb.dto.request.UpdateProfileRequest;
import com.mathweb.dto.response.*;
import com.mathweb.repository.UserQuestProgressRepository;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserQuestProgressRepository questProgressRepository;

    public UserController(UserService userService,
                          UserQuestProgressRepository questProgressRepository) {
        this.userService = userService;
        this.questProgressRepository = questProgressRepository;
    }

    // ===== ANY AUTHENTICATED USER =====

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal) {
        UserResponse user = userService.getCurrentUser(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("User retrieved", user));
    }

    @GetMapping("/me/stats")
    public ResponseEntity<ApiResponse<UserStatsResponse>> getMyStats(
            @AuthenticationPrincipal UserPrincipal principal) {
        UserStatsResponse stats = userService.getUserStats(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Stats retrieved", stats));
    }

    @GetMapping("/me/progress")
    public ResponseEntity<ApiResponse<List<QuestProgressResponse>>> getMyProgress(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<QuestProgressResponse> progress =
                questProgressRepository
                        .findByUserId(principal.getId())
                        .stream()
                        .map(p -> QuestProgressResponse.builder()
                                .questId(p.getQuest().getId())
                                .questTitle(p.getQuest().getTitle())
                                .status(p.getStatus())
                                .problemsSolved(p.getProblemsSolved())
                                .totalProblems(p.getTotalProblems())
                                .progressPercentage(p.getProgressPercentage())
                                .xpEarned(p.getXpEarned())
                                .startedAt(p.getStartedAt())
                                .completedAt(p.getCompletedAt())
                                .build())
                        .toList();
        return ResponseEntity.ok(
                ApiResponse.success("Progress retrieved", progress));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserResponse user = userService.updateProfile(
                principal.getId(), request);
        return ResponseEntity.ok(
                ApiResponse.success("Profile updated", user));
    }

    @PostMapping(value = "/me/avatar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserResponse>> updateAvatar(
            @RequestPart("file") MultipartFile avatar,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserResponse user = userService.updateAvatar(
                principal.getId(), avatar);
        return ResponseEntity.ok(
                ApiResponse.success("Avatar updated", user));
    }

    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        userService.changePassword(principal.getId(), request);
        return ResponseEntity.ok(
                ApiResponse.success("Password changed successfully"));
    }

    // ===== ADMIN ONLY =====

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable Long id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("User retrieved", user));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<UserResponse> users = userService.getAllUsers(page, size);
        return ResponseEntity.ok(
                ApiResponse.success("Users retrieved", users));
    }
}
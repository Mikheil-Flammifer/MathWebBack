package com.mathweb.controller;

import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.dto.response.UserResponse;
import com.mathweb.enums.Role;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(ApiResponse.success("Users retrieved",
                userService.getAllUsers(safePage, safeSize)));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRole(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam Role role) {
        return ResponseEntity.ok(ApiResponse.success("User role updated",
                userService.updateUserRole(principal.getId(), id, role)));
    }

    @PutMapping("/users/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        userService.deactivateUser(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("User deactivated"));
    }

    @PutMapping("/users/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activateUser(@PathVariable Long id) {
        userService.activateUser(id);
        return ResponseEntity.ok(ApiResponse.success("User activated"));
    }
}
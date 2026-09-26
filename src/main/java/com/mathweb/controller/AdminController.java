package com.mathweb.controller;

import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.dto.response.UserResponse;
import com.mathweb.enums.Role;
import com.mathweb.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
        PageResponse<UserResponse> users = userService.getAllUsers(page, size);
        return ResponseEntity.ok(ApiResponse.success("Users retrieved", users));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRole(
            @PathVariable Long id,
            @RequestParam Role role) {
        UserResponse user = userService.updateUserRole(id, role);
        return ResponseEntity.ok(ApiResponse.success("User role updated", user));
    }

    @PutMapping("/users/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deactivated"));
    }

    @PutMapping("/users/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activateUser(@PathVariable Long id) {
        userService.activateUser(id);
        return ResponseEntity.ok(ApiResponse.success("User activated"));
    }
}
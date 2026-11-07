package com.mathweb.controller;

import com.mathweb.dto.request.GrantAccessRequest;
import com.mathweb.dto.response.AccessGrantResponse;
import com.mathweb.dto.response.AdminUserResponse;
import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.dto.response.UserResponse;
import com.mathweb.enums.Role;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.UserService;
import com.mathweb.service.impl.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;
    private final AdminUserService adminUserService;

    public AdminController(UserService userService, AdminUserService adminUserService) {
        this.userService = userService;
        this.adminUserService = adminUserService;
    }

    /** role, active and hasAccess are optional filters. keyword matches email and names. */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean hasAccess) {
        return ResponseEntity.ok(ApiResponse.success("Users retrieved",
                adminUserService.listUsers(page, size, keyword, role, active, hasAccess)));
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

    // ===== FREE ACCESS =====

    @PostMapping("/users/{id}/grant-access")
    public ResponseEntity<ApiResponse<AdminUserResponse>> grantAccess(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody GrantAccessRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Access granted",
                adminUserService.grantAccess(principal.getId(), id, request)));
    }

    @PostMapping("/users/{id}/revoke-access")
    public ResponseEntity<ApiResponse<AdminUserResponse>> revokeAccess(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam(required = false) String note) {
        return ResponseEntity.ok(ApiResponse.success("Access revoked",
                adminUserService.revokeAccess(principal.getId(), id, note)));
    }

    @GetMapping("/users/{id}/access-history")
    public ResponseEntity<ApiResponse<List<AccessGrantResponse>>> accessHistory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Access history retrieved",
                adminUserService.getHistory(id)));
    }
}
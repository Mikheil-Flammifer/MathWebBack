package com.mathweb.controller;

import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.UserResponse;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal) {
        UserResponse user = userService.getCurrentUser(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("User retrieved", user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("User retrieved", user));
    }
}
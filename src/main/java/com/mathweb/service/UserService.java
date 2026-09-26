package com.mathweb.service;

import com.mathweb.dto.response.PageResponse;
import com.mathweb.dto.response.UserResponse;
import com.mathweb.enums.Role;

public interface UserService {
    UserResponse getUserById(Long id);
    UserResponse getCurrentUser(Long userId);
    PageResponse<UserResponse> getAllUsers(int page, int size);
    UserResponse updateUserRole(Long userId, Role role);
    void deactivateUser(Long userId);
    void activateUser(Long userId);
}
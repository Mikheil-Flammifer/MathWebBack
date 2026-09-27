package com.mathweb.service;

import com.mathweb.dto.request.ChangePasswordRequest;
import com.mathweb.dto.request.UpdateProfileRequest;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.dto.response.UserResponse;
import com.mathweb.dto.response.UserStatsResponse;
import com.mathweb.enums.Role;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    UserResponse getUserById(Long id);
    UserResponse getCurrentUser(Long userId);
    PageResponse<UserResponse> getAllUsers(int page, int size);
    UserResponse updateUserRole(Long userId, Role role);
    UserResponse updateProfile(Long userId, UpdateProfileRequest request);
    UserResponse updateAvatar(Long userId, MultipartFile avatar);
    UserStatsResponse getUserStats(Long userId);
    void changePassword(Long userId, ChangePasswordRequest request);
    void deactivateUser(Long userId);
    void activateUser(Long userId);
}
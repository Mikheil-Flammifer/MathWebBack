package com.mathweb.dto.response;

import com.mathweb.enums.Role;
import com.mathweb.enums.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private Boolean emailVerified;
    private Boolean active;
    private String avatarUrl;
    private LocalDateTime createdAt;

    private boolean hasActiveAccess;
    private SubscriptionStatus subscriptionStatus;   // null = never had access
    private LocalDateTime accessEndsAt;
}
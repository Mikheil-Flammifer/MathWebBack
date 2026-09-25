package com.mathweb.entity;

import com.mathweb.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "stripe_customer_id", unique = true)
    private String stripeCustomerId;

    // ===== RELATIONS =====

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OtpCode> otpCodes = new ArrayList<>();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Subscription subscription;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserQuestProgress> questProgresses = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProblemAttempt> problemAttempts = new ArrayList<>();

    @OneToMany(mappedBy = "uploadedBy", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Video> uploadedVideos = new ArrayList<>();

    // ===== HELPERS =====

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean hasActiveSubscription() {
        return subscription != null && subscription.isCurrentlyActive();
    }
}
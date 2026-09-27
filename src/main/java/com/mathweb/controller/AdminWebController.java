package com.mathweb.controller;

import com.mathweb.dto.request.*;
import com.mathweb.dto.response.*;
import com.mathweb.entity.User;
import com.mathweb.enums.Role;
import com.mathweb.enums.SubscriptionStatus;
import com.mathweb.enums.VideoStatus;
import com.mathweb.repository.SubscriptionRepository;
import com.mathweb.repository.UserRepository;
import com.mathweb.repository.VideoRepository;
import com.mathweb.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminWebController {

    private final AuthService authService;
    private final UserService userService;
    private final QuestService questService;
    private final VideoService videoService;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final VideoRepository videoRepository;

    public AdminWebController(AuthService authService,
                              UserService userService,
                              QuestService questService,
                              VideoService videoService,
                              UserRepository userRepository,
                              SubscriptionRepository subscriptionRepository,
                              VideoRepository videoRepository) {
        this.authService = authService;
        this.userService = userService;
        this.questService = questService;
        this.videoService = videoService;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.videoRepository = videoRepository;
    }

    // ===== AUTH =====

    @GetMapping("/login")
    public String loginPage(Model model) {
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {
        try {
            LoginRequest request = new LoginRequest();
            request.setEmail(email);
            request.setPassword(password);
            AuthResponse response = authService.login(request);

            // Check if user is admin
            if (response.getUser().getRole() != Role.ADMIN) {
                redirectAttributes.addFlashAttribute("error",
                        "Access denied. Admin only.");
                return "redirect:/admin/login";
            }

            session.setAttribute("adminToken", response.getAccessToken());
            session.setAttribute("adminUser", response.getUser());
            return "redirect:/admin/dashboard";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Invalid email or password");
            return "redirect:/admin/login";
        }
    }

    @GetMapping("/verify-otp")
    public String verifyOtpPage(@RequestParam String email, Model model) {
        model.addAttribute("email", email);
        return "auth/verify-otp";
    }

    @PostMapping("/verify-otp")
    public String verifyOtp(@RequestParam String email,
                            @RequestParam String code,
                            RedirectAttributes redirectAttributes) {
        try {
            VerifyOtpRequest request = new VerifyOtpRequest();
            request.setEmail(email);
            request.setCode(code);
            request.setPurpose("EMAIL_VERIFICATION");
            authService.verifyOtp(request);
            redirectAttributes.addFlashAttribute("success",
                    "Email verified! You can now login.");
            return "redirect:/admin/login";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/verify-otp?email=" + email;
        }
    }

    @PostMapping("/resend-otp")
    public String resendOtp(@RequestParam String email,
                            RedirectAttributes redirectAttributes) {
        try {
            ResendOtpRequest request = new ResendOtpRequest();
            request.setEmail(email);
            request.setPurpose("EMAIL_VERIFICATION");
            authService.resendOtp(request);
            redirectAttributes.addFlashAttribute("success",
                    "New OTP sent to your email");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/verify-otp?email=" + email;
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        String token = (String) session.getAttribute("adminToken");
        authService.logout(token);
        session.invalidate();
        return "redirect:/admin/login";
    }

    // ===== DASHBOARD =====

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        long totalUsers = userRepository.count();
        long activeSubscribers = subscriptionRepository
                .findByStatus(SubscriptionStatus.ACTIVE).size();
        long totalVideos = videoRepository
                .findByStatus(VideoStatus.PUBLISHED,
                        PageRequest.of(0, 1)).getTotalElements();
        long totalQuests = questService.getAllQuests(null).size();

        // Recent 5 users
        Page<User> recentUsersPage = userRepository.findAll(
                PageRequest.of(0, 5, Sort.by("createdAt").descending()));

        List<UserResponse> recentUsers = recentUsersPage.getContent()
                .stream()
                .map(u -> UserResponse.builder()
                        .id(u.getId())
                        .firstName(u.getFirstName())
                        .lastName(u.getLastName())
                        .email(u.getEmail())
                        .role(u.getRole())
                        .emailVerified(u.getEmailVerified())
                        .hasActiveSubscription(u.hasActiveSubscription())
                        .createdAt(u.getCreatedAt())
                        .build())
                .toList();

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("activeSubscribers", activeSubscribers);
        model.addAttribute("totalVideos", totalVideos);
        model.addAttribute("totalQuests", totalQuests);
        model.addAttribute("recentUsers", recentUsers);

        return "admin/dashboard";
    }

    // ===== USERS =====

    @GetMapping("/users")
    public String users(Model model,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
        PageResponse<UserResponse> usersPage =
                userService.getAllUsers(page, size);

        model.addAttribute("users", usersPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", usersPage.getTotalPages());
        model.addAttribute("totalUsers", usersPage.getTotalElements());

        return "admin/users";
    }

    @PostMapping("/users/{id}/role")
    public String updateUserRole(@PathVariable Long id,
                                 @RequestParam String role,
                                 RedirectAttributes redirectAttributes) {
        try {
            userService.updateUserRole(id, Role.valueOf(role));
            redirectAttributes.addFlashAttribute("success",
                    "User role updated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/deactivate")
    public String deactivateUser(@PathVariable Long id,
                                 RedirectAttributes redirectAttributes) {
        try {
            userService.deactivateUser(id);
            redirectAttributes.addFlashAttribute("success",
                    "User deactivated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/activate")
    public String activateUser(@PathVariable Long id,
                               RedirectAttributes redirectAttributes) {
        try {
            userService.activateUser(id);
            redirectAttributes.addFlashAttribute("success",
                    "User activated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ===== QUESTS =====

    @GetMapping("/quests")
    public String quests(Model model) {
        List<QuestResponse> quests = questService.getAllQuests(null);
        model.addAttribute("quests", quests);
        return "admin/quests";
    }

    @PostMapping("/quests/create")
    public String createQuest(@RequestParam String title,
                              @RequestParam(required = false) String description,
                              @RequestParam String difficultyLevel,
                              @RequestParam(defaultValue = "0") Integer xpReward,
                              @RequestParam(defaultValue = "0") Integer positionX,
                              @RequestParam(defaultValue = "0") Integer positionY,
                              RedirectAttributes redirectAttributes) {
        try {
            CreateQuestRequest request = new CreateQuestRequest();
            request.setTitle(title);
            request.setDescription(description);
            request.setDifficultyLevel(
                    com.mathweb.enums.DifficultyLevel.valueOf(difficultyLevel));
            request.setXpReward(xpReward);
            request.setPositionX(positionX);
            request.setPositionY(positionY);
            questService.createQuest(request);
            redirectAttributes.addFlashAttribute("success",
                    "Quest created successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/quests";
    }

    @PostMapping("/quests/{id}/publish")
    public String publishQuest(@PathVariable Long id,
                               RedirectAttributes redirectAttributes) {
        try {
            questService.publishQuest(id);
            redirectAttributes.addFlashAttribute("success",
                    "Quest published successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/quests";
    }

    @PostMapping("/quests/{id}/delete")
    public String deleteQuest(@PathVariable Long id,
                              RedirectAttributes redirectAttributes) {
        try {
            questService.deleteQuest(id);
            redirectAttributes.addFlashAttribute("success",
                    "Quest deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/quests";
    }

    // ===== VIDEOS =====

    @GetMapping("/videos")
    public String videos(Model model,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size) {
        PageResponse<VideoResponse> videosPage =
                videoService.getAllVideos(page, size, null);
        model.addAttribute("videos", videosPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", videosPage.getTotalPages());
        return "admin/videos";
    }

    @PostMapping("/videos/{id}/delete")
    public String deleteVideo(@PathVariable Long id,
                              RedirectAttributes redirectAttributes) {
        try {
            videoService.deleteVideo(id, null);
            redirectAttributes.addFlashAttribute("success",
                    "Video deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/videos";
    }
}
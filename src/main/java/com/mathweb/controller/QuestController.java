package com.mathweb.controller;

import com.mathweb.dto.request.CreateQuestRequest;
import com.mathweb.dto.request.SaveQuestMapRequest;
import com.mathweb.dto.request.UpdateQuestRequest;
import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.dto.response.QuestMapResponse;
import com.mathweb.dto.response.QuestResponse;
import com.mathweb.enums.DifficultyLevel;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.QuestService;
import com.mathweb.service.impl.QuestMapService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quests")
public class QuestController {

    private final QuestService questService;
    private final QuestMapService questMapService;

    public QuestController(QuestService questService, QuestMapService questMapService) {
        this.questService = questService;
        this.questMapService = questMapService;
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<PageResponse<QuestResponse>>> getAllQuestsForAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) DifficultyLevel level,
            @RequestParam(required = false) Boolean published,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.success("Quests retrieved",
                questService.getAllQuestsForAdmin(page, size, level, published, keyword)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<QuestResponse>>> getAllQuests(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) DifficultyLevel level,
            @RequestParam(required = false) String keyword) {
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(ApiResponse.success("Quests retrieved",
                questService.getAllQuests(userId, page, size, level, keyword)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuestResponse>> getQuestById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : null;
        QuestResponse quest = questService.getQuestById(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Quest retrieved", quest));
    }

    @GetMapping("/{id}/map")
    public ResponseEntity<ApiResponse<QuestMapResponse>> getQuestMap(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(ApiResponse.success("Quest map retrieved",
                questMapService.getMap(id, userId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<QuestResponse>> createQuest(
            @Valid @RequestBody CreateQuestRequest request) {
        QuestResponse quest = questService.createQuest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Quest created", quest));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<QuestResponse>> updateQuest(
            @PathVariable Long id,
            @Valid @RequestBody UpdateQuestRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Quest updated",
                questService.updateQuest(id, request)));
    }

    @PutMapping("/{id}/map")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<QuestMapResponse>> saveQuestMap(
            @PathVariable Long id,
            @Valid @RequestBody SaveQuestMapRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Quest map saved",
                questMapService.saveMap(id, request)));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<Void>> publishQuest(@PathVariable Long id) {
        questService.publishQuest(id);
        return ResponseEntity.ok(ApiResponse.success("Quest published"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteQuest(@PathVariable Long id) {
        questService.deleteQuest(id);
        return ResponseEntity.ok(ApiResponse.success("Quest deleted"));
    }
}
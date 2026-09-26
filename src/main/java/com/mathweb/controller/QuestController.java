package com.mathweb.controller;

import com.mathweb.dto.request.CreateQuestRequest;
import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.QuestResponse;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.QuestService;
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

    public QuestController(QuestService questService) {
        this.questService = questService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<QuestResponse>>> getAllQuests(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : null;
        List<QuestResponse> quests = questService.getAllQuests(userId);
        return ResponseEntity.ok(ApiResponse.success("Quests retrieved", quests));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuestResponse>> getQuestById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : null;
        QuestResponse quest = questService.getQuestById(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Quest retrieved", quest));
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
            @Valid @RequestBody CreateQuestRequest request) {
        QuestResponse quest = questService.updateQuest(id, request);
        return ResponseEntity.ok(ApiResponse.success("Quest updated", quest));
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
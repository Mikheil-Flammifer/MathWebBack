package com.mathweb.service;

import com.mathweb.dto.request.CreateQuestRequest;
import com.mathweb.dto.request.UpdateQuestRequest;
import com.mathweb.dto.response.QuestResponse;

import java.util.List;

public interface QuestService {
    List<QuestResponse> getAllQuestsForAdmin();
    QuestResponse createQuest(CreateQuestRequest request);
    QuestResponse getQuestById(Long id, Long userId);
    List<QuestResponse> getAllQuests(Long userId);
    QuestResponse updateQuest(Long id, UpdateQuestRequest request);
    void deleteQuest(Long id);
    void publishQuest(Long id);
}
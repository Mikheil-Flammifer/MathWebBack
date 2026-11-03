package com.mathweb.service;

import com.mathweb.dto.request.CreateQuestRequest;
import com.mathweb.dto.request.UpdateQuestRequest;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.dto.response.QuestResponse;
import com.mathweb.enums.DifficultyLevel;

public interface QuestService {

    PageResponse<QuestResponse> getAllQuestsForAdmin(int page, int size,
                                                     DifficultyLevel level,
                                                     Boolean published,
                                                     String keyword);

    PageResponse<QuestResponse> getAllQuests(Long userId, int page, int size,
                                             DifficultyLevel level, String keyword);

    QuestResponse createQuest(CreateQuestRequest request);

    QuestResponse getQuestById(Long id, Long userId);

    QuestResponse updateQuest(Long id, UpdateQuestRequest request);

    void deleteQuest(Long id);

    void publishQuest(Long id);
}
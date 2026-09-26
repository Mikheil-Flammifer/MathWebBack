package com.mathweb.service.impl;

import com.mathweb.dto.request.CreateQuestRequest;
import com.mathweb.dto.response.QuestProgressResponse;
import com.mathweb.dto.response.QuestResponse;
import com.mathweb.entity.Quest;
import com.mathweb.entity.UserQuestProgress;
import com.mathweb.enums.QuestStatus;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.ProblemRepository;
import com.mathweb.repository.QuestRepository;
import com.mathweb.repository.UserQuestProgressRepository;
import com.mathweb.repository.VideoRepository;
import com.mathweb.service.QuestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class QuestServiceImpl implements QuestService {

    private static final Logger log = LoggerFactory.getLogger(QuestServiceImpl.class);

    private final QuestRepository questRepository;
    private final UserQuestProgressRepository progressRepository;
    private final ProblemRepository problemRepository;
    private final VideoRepository videoRepository;

    public QuestServiceImpl(QuestRepository questRepository,
                            UserQuestProgressRepository progressRepository,
                            ProblemRepository problemRepository,
                            VideoRepository videoRepository) {
        this.questRepository = questRepository;
        this.progressRepository = progressRepository;
        this.problemRepository = problemRepository;
        this.videoRepository = videoRepository;
    }

    @Override
    @Transactional
    public QuestResponse createQuest(CreateQuestRequest request) {
        Quest quest = Quest.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .difficultyLevel(request.getDifficultyLevel())
                .positionX(request.getPositionX())
                .positionY(request.getPositionY())
                .xpReward(request.getXpReward() != null ? request.getXpReward() : 0)
                .published(false)
                .build();

        // Set prerequisites (DAG edges)
        if (request.getPrerequisiteIds() != null && !request.getPrerequisiteIds().isEmpty()) {
            List<Quest> prerequisites = questRepository.findAllById(request.getPrerequisiteIds());
            quest.setPrerequisites(prerequisites);
        }

        questRepository.save(quest);
        log.info("Quest created: {}", quest.getTitle());

        return mapToQuestResponse(quest, null);
    }

    @Override
    @Transactional(readOnly = true)
    public QuestResponse getQuestById(Long id, Long userId) {
        Quest quest = questRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quest", id));

        UserQuestProgress progress = userId != null
                ? progressRepository.findByUserIdAndQuestId(userId, id).orElse(null)
                : null;

        return mapToQuestResponse(quest, progress);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestResponse> getAllQuests(Long userId) {
        List<Quest> quests = questRepository.findByPublishedTrueOrderByDifficultyLevelAsc();

        List<Long> completedIds = userId != null
                ? progressRepository.findCompletedQuestIdsByUserId(userId)
                : List.of();

        return quests.stream()
                .map(quest -> {
                    Optional<UserQuestProgress> progress = userId != null
                            ? progressRepository.findByUserIdAndQuestId(userId, quest.getId())
                            : Optional.empty();

                    QuestResponse response = mapToQuestResponse(quest, progress.orElse(null));

                    // Determine if quest is unlocked for this user
                    if (userId != null && response.getUserStatus() == null) {
                        boolean unlocked = quest.isUnlocked(completedIds);
                        response.setUserStatus(unlocked ? QuestStatus.AVAILABLE : QuestStatus.LOCKED);
                    }

                    return response;
                })
                .toList();
    }

    @Override
    @Transactional
    public QuestResponse updateQuest(Long id, CreateQuestRequest request) {
        Quest quest = questRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quest", id));

        quest.setTitle(request.getTitle());
        quest.setDescription(request.getDescription());
        quest.setDifficultyLevel(request.getDifficultyLevel());
        quest.setPositionX(request.getPositionX());
        quest.setPositionY(request.getPositionY());

        if (request.getPrerequisiteIds() != null) {
            List<Quest> prerequisites = questRepository.findAllById(request.getPrerequisiteIds());
            quest.setPrerequisites(prerequisites);
        }

        questRepository.save(quest);
        return mapToQuestResponse(quest, null);
    }

    @Override
    @Transactional
    public void deleteQuest(Long id) {
        Quest quest = questRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quest", id));
        questRepository.delete(quest);
        log.info("Quest deleted: {}", id);
    }

    @Override
    @Transactional
    public void publishQuest(Long id) {
        Quest quest = questRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quest", id));
        quest.setPublished(true);
        questRepository.save(quest);
        log.info("Quest published: {}", id);
    }

    // ===== PRIVATE HELPERS =====

    private QuestResponse mapToQuestResponse(Quest quest, UserQuestProgress progress) {
        int totalProblems = problemRepository.countByQuestId(quest.getId());
        int totalVideos = (int) videoRepository.countByUploadedById(quest.getId());

        QuestResponse response = QuestResponse.builder()
                .id(quest.getId())
                .title(quest.getTitle())
                .description(quest.getDescription())
                .iconUrl(quest.getIconUrl())
                .difficultyLevel(quest.getDifficultyLevel())
                .positionX(quest.getPositionX())
                .positionY(quest.getPositionY())
                .published(quest.getPublished())
                .xpReward(quest.getXpReward())
                .prerequisiteIds(quest.getPrerequisites().stream()
                        .map(Quest::getId).toList())
                .dependentIds(quest.getDependents().stream()
                        .map(Quest::getId).toList())
                .totalProblems(totalProblems)
                .totalVideos(totalVideos)
                .createdAt(quest.getCreatedAt())
                .build();

        if (progress != null) {
            response.setUserStatus(progress.getStatus());
            response.setUserProblemsSolved(progress.getProblemsSolved());
            response.setUserProgressPercentage(progress.getProgressPercentage());
        }

        return response;
    }
}
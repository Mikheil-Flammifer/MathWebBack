package com.mathweb.service.impl;

import com.mathweb.dto.request.CreateQuestRequest;
import com.mathweb.dto.request.UpdateQuestRequest;
import com.mathweb.dto.response.QuestProgressResponse;
import com.mathweb.dto.response.QuestResponse;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.enums.DifficultyLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.function.Function;
import com.mathweb.entity.Quest;
import com.mathweb.entity.UserQuestProgress;
import com.mathweb.entity.AnswerOption;
import com.mathweb.entity.Problem;
import com.mathweb.service.FileStorageService;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.ArrayList;
import com.mathweb.enums.QuestStatus;
import com.mathweb.enums.VideoStatus;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.ProblemRepository;
import com.mathweb.repository.QuestRepository;
import com.mathweb.repository.UserQuestProgressRepository;
import com.mathweb.repository.VideoRepository;
import com.mathweb.util.RoleUtil;
import com.mathweb.service.QuestService;
import com.mathweb.service.impl.QuestMapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class QuestServiceImpl implements QuestService {

    private static final Logger log = LoggerFactory.getLogger(QuestServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 20;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final QuestMapService questMapService;
    private final QuestRepository questRepository;
    private final UserQuestProgressRepository progressRepository;
    private final ProblemRepository problemRepository;
    private final VideoRepository videoRepository;
    private final FileStorageService fileStorageService;

    public QuestServiceImpl(QuestMapService questMapService, QuestRepository questRepository,
                            UserQuestProgressRepository progressRepository,
                            ProblemRepository problemRepository,
                            VideoRepository videoRepository,
                            FileStorageService fileStorageService) {
        this.questMapService = questMapService;
        this.questRepository = questRepository;
        this.progressRepository = progressRepository;
        this.problemRepository = problemRepository;
        this.videoRepository = videoRepository;
        this.fileStorageService = fileStorageService;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuestResponse> getAllQuestsForAdmin(int page, int size,
                                                            DifficultyLevel level,
                                                            Boolean published,
                                                            String keyword) {
        Page<Quest> questPage = questRepository.findForAdmin(
                published, level, normalizeKeyword(keyword), pageRequest(page, size));
        return mapToPageResponse(questPage, quest -> mapToQuestResponse(quest, null));
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

        // Drafts are invisible to students: same answer as "does not exist"
        if (!Boolean.TRUE.equals(quest.getPublished()) && !RoleUtil.isStaff()) {
            throw new ResourceNotFoundException("Quest", id);
        }

        UserQuestProgress progress = userId != null
                ? progressRepository.findByUserIdAndQuestId(userId, id).orElse(null)
                : null;

        return mapToQuestResponse(quest, progress);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuestResponse> getAllQuests(Long userId, int page, int size,
                                                    DifficultyLevel level, String keyword) {
        Page<Quest> questPage = questRepository.findPublishedFiltered(
                level, normalizeKeyword(keyword), pageRequest(page, size));

        List<Long> completedIds = userId != null
                ? progressRepository.findCompletedQuestIdsByUserId(userId)
                : List.of();

        return mapToPageResponse(questPage, quest -> {
            UserQuestProgress progress = userId != null
                    ? progressRepository.findByUserIdAndQuestId(userId, quest.getId()).orElse(null)
                    : null;

            QuestResponse response = mapToQuestResponse(quest, progress);

            // Determine if quest is unlocked for this user
            if (userId != null && response.getUserStatus() == null) {
                response.setUserStatus(quest.isUnlocked(completedIds)
                        ? QuestStatus.AVAILABLE : QuestStatus.LOCKED);
            }
            return response;
        });
    }

    @Override
    @Transactional
    public QuestResponse updateQuest(Long id, UpdateQuestRequest request) {
        Quest quest = questRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quest", id));

        if (request.getTitle() != null) quest.setTitle(request.getTitle());
        if (request.getDescription() != null) quest.setDescription(request.getDescription());
        if (request.getDifficultyLevel() != null) quest.setDifficultyLevel(request.getDifficultyLevel());
        if (request.getPositionX() != null) quest.setPositionX(request.getPositionX());
        if (request.getPositionY() != null) quest.setPositionY(request.getPositionY());
        if (request.getXpReward() != null) quest.setXpReward(request.getXpReward());

        if (request.getPrerequisiteIds() != null) {
            List<Quest> prereqs = questRepository.findAllById(request.getPrerequisiteIds());
            if (prereqs.size() != request.getPrerequisiteIds().stream().distinct().count()) {
                throw new IllegalArgumentException("Some prerequisite quests do not exist");
            }
            for (Quest p : prereqs) {
                if (reaches(p, id, new java.util.HashSet<>())) {
                    throw new IllegalArgumentException("Prerequisites would create a cycle");
                }
            }
            quest.setPrerequisites(prereqs);
        }

        questRepository.save(quest);
        return mapToQuestResponse(quest, null);
    }

    // true if `from` or any of its prerequisites is the quest with targetId
    private boolean reaches(Quest from, Long targetId, java.util.Set<Long> seen) {
        if (from.getId().equals(targetId)) return true;
        if (!seen.add(from.getId())) return false;
        for (Quest p : from.getPrerequisites()) {
            if (reaches(p, targetId, seen)) return true;
        }
        return false;
    }

    @Override
    @Transactional
    public void deleteQuest(Long id) {
        Quest quest = questRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quest", id));

        // Collect file paths BEFORE the rows are gone
        List<String> files = new ArrayList<>();
        for (Problem p : quest.getProblems()) {
            files.add(p.getQuestionImagePath());
            files.add(p.getExplanationImagePath());
            for (AnswerOption o : p.getAnswerOptions()) {
                files.add(o.getOptionImagePath());
            }
        }

        // Videos survive: they just lose their quest link
        videoRepository.detachFromQuest(id);

        questRepository.delete(quest);   // problems, attempts and progress go with it
        deleteFilesAfterCommit(files);
        log.info("Quest deleted: {}", id);
    }

    @Override
    @Transactional
    public void publishQuest(Long id) {
        Quest quest = questRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quest", id));
        questMapService.assertMapValid(id);
        quest.setPublished(true);
        questRepository.save(quest);
        log.info("Quest published: {}", id);
    }

    // ===== PRIVATE HELPERS =====

    private QuestResponse mapToQuestResponse(Quest quest, UserQuestProgress progress) {
        int totalProblems = problemRepository.countByQuestId(quest.getId());
        int totalVideos = (int) videoRepository.countByQuestIdAndStatus(quest.getId(), VideoStatus.PUBLISHED);

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

    private PageRequest pageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // Same order as the old list: difficulty, then id (stable for paging)
        return PageRequest.of(safePage, safeSize,
                Sort.by("difficultyLevel").ascending().and(Sort.by("id").ascending()));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) return "";
        String k = keyword.trim();
        return k.length() > MAX_KEYWORD_LENGTH ? k.substring(0, MAX_KEYWORD_LENGTH) : k;
    }

    private PageResponse<QuestResponse> mapToPageResponse(Page<Quest> page,
                                                          Function<Quest, QuestResponse> mapper) {
        return PageResponse.<QuestResponse>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    /** Removes files only if the surrounding transaction commits. */
    private void deleteFilesAfterCommit(List<String> paths) {
        List<String> files = paths.stream().filter(p -> p != null && !p.isBlank()).toList();
        if (files.isEmpty()) return;
        Runnable cleanup = () -> files.forEach(fileStorageService::deleteFile);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
    }
}
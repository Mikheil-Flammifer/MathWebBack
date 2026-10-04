package com.mathweb.service.impl;

import com.mathweb.dto.request.CreateProblemRequest;
import com.mathweb.dto.request.SubmitAnswerRequest;
import com.mathweb.dto.request.UpdateProblemRequest;
import com.mathweb.dto.response.AnswerOptionResponse;
import com.mathweb.dto.response.AnswerResultResponse;
import com.mathweb.dto.response.ProblemResponse;
import com.mathweb.entity.*;
import com.mathweb.enums.NodeStatus;
import com.mathweb.enums.ProblemType;
import com.mathweb.enums.QuestStatus;
import com.mathweb.exception.BadRequestException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.service.FileStorageService;
import com.mathweb.util.FileUtil;
import com.mathweb.util.RoleUtil;
import com.mathweb.util.SanitizationUtil;
import com.mathweb.repository.*;
import com.mathweb.service.ProblemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ProblemServiceImpl implements ProblemService {

    private static final Logger log = LoggerFactory.getLogger(ProblemServiceImpl.class);

    private final ProblemRepository problemRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final ProblemAttemptRepository attemptRepository;
    private final UserRepository userRepository;
    private final QuestRepository questRepository;
    private final UserQuestProgressRepository progressRepository;
    private final CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;
    private final QuestMapService questMapService;

    public ProblemServiceImpl(ProblemRepository problemRepository,
                              AnswerOptionRepository answerOptionRepository,
                              ProblemAttemptRepository attemptRepository,
                              UserRepository userRepository,
                              QuestRepository questRepository,
                              UserQuestProgressRepository progressRepository, CategoryRepository categoryRepository, FileStorageService fileStorageService, QuestMapService questMapService) {
        this.problemRepository = problemRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.attemptRepository = attemptRepository;
        this.userRepository = userRepository;
        this.questRepository = questRepository;
        this.progressRepository = progressRepository;
        this.categoryRepository = categoryRepository;
        this.fileStorageService = fileStorageService;
        this.questMapService = questMapService;
    }

    @Override
    @Transactional
    public ProblemResponse createProblem(CreateProblemRequest request) {
        Quest quest = questRepository.findById(request.getQuestId())
                .orElseThrow(() -> new ResourceNotFoundException("Quest", request.getQuestId()));

        validateContent(request.getProblemType(), request.getCorrectAnswer(),
                request.getAnswerOptions().stream()
                        .map(o -> Boolean.TRUE.equals(o.getIsCorrect())).toList());

        Problem problem = Problem.builder()
                .questionText(SanitizationUtil.sanitizeText(request.getQuestionText()))
                .problemType(request.getProblemType())
                .difficultyLevel(request.getDifficultyLevel())
                .correctAnswer(request.getCorrectAnswer())
                .explanation(request.getExplanation())
                .orderIndex(request.getOrderIndex())
                .xpReward(request.getXpReward())
                .category(resolveCategory(request.getCategoryId()))
                .quest(quest)
                .build();
        problemRepository.save(problem);

        if (request.getProblemType() == ProblemType.MULTIPLE_CHOICE) {
            for (int i = 0; i < request.getAnswerOptions().size(); i++) {
                CreateProblemRequest.AnswerOptionRequest opt = request.getAnswerOptions().get(i);
                answerOptionRepository.save(AnswerOption.builder()
                        .problem(problem)
                        .optionText(opt.getOptionText())
                        .isCorrect(Boolean.TRUE.equals(opt.getIsCorrect()))
                        .orderIndex(i)
                        .build());
            }
        }

        questMapService.attachNewProblem(problem);

        log.info("Problem created in quest {}", quest.getId());
        return mapToProblemResponse(problem, null);
    }

    @Override
    @Transactional(readOnly = true)
    public ProblemResponse getProblemById(Long id, Long userId) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem", id));

        if (!RoleUtil.isStaff()) {
            Quest quest = problem.getQuest();
            if (!Boolean.TRUE.equals(quest.getPublished())) {
                throw new ResourceNotFoundException("Problem", id);
            }
            NodeStatus status = questMapService.getStatuses(quest.getId(), userId).get(id);
            if (status == null || status == NodeStatus.LOCKED) {
                throw new BadRequestException("This problem is locked. Solve a neighbouring problem on the map first.");
            }
        }

        ProblemAttempt attempt = userId != null
                ? attemptRepository.findByUserIdAndProblemId(userId, id).orElse(null)
                : null;
        return mapToProblemResponse(problem, attempt);
    }

    @Override
    @Transactional
    public ProblemResponse updateProblem(Long id, UpdateProblemRequest request) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem", id));

        validateContent(request.getProblemType(), request.getCorrectAnswer(),
                request.getAnswerOptions().stream()
                        .map(o -> Boolean.TRUE.equals(o.getIsCorrect())).toList());

        problem.setQuestionText(SanitizationUtil.sanitizeText(request.getQuestionText()));
        problem.setProblemType(request.getProblemType());
        problem.setDifficultyLevel(request.getDifficultyLevel());
        problem.setCorrectAnswer(request.getCorrectAnswer());
        problem.setExplanation(request.getExplanation());
        problem.setOrderIndex(request.getOrderIndex());
        problem.setXpReward(request.getXpReward());
        problem.setMaxAttempts(request.getMaxAttempts() != null ? request.getMaxAttempts() : 3);
        problem.setCategory(resolveCategory(request.getCategoryId()));

        // Replace options (orphanRemoval deletes the old rows)
        problem.getAnswerOptions().clear();
        if (request.getProblemType() == ProblemType.MULTIPLE_CHOICE) {
            for (int i = 0; i < request.getAnswerOptions().size(); i++) {
                CreateProblemRequest.AnswerOptionRequest opt = request.getAnswerOptions().get(i);
                problem.getAnswerOptions().add(AnswerOption.builder()
                        .problem(problem)
                        .optionText(opt.getOptionText())
                        .isCorrect(Boolean.TRUE.equals(opt.getIsCorrect()))
                        .orderIndex(i)
                        .build());
            }
        }

        problemRepository.save(problem);
        log.info("Problem updated: {}", id);
        return mapToProblemResponse(problem, null);
    }

    @Override
    @Transactional
    public ProblemResponse uploadQuestionImage(Long problemId,
                                               MultipartFile image) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Problem", problemId));

        FileUtil.validateImage(image);

        // Delete old image if exists
        if (problem.getQuestionImagePath() != null) {
            // inject fileStorageService
        }

        String imagePath = fileStorageService.storeImage(image);
        problem.setQuestionImagePath(imagePath);
        problemRepository.save(problem);

        log.info("Question image uploaded for problem {}", problemId);
        return mapToProblemResponse(problem, null);
    }

    @Override
    @Transactional
    public ProblemResponse uploadExplanationImage(Long problemId,
                                                  MultipartFile image) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Problem", problemId));

        FileUtil.validateImage(image);

        String imagePath = fileStorageService.storeImage(image);
        problem.setExplanationImagePath(imagePath);
        problemRepository.save(problem);

        log.info("Explanation image uploaded for problem {}", problemId);
        return mapToProblemResponse(problem, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProblemResponse> getProblemsByQuest(Long questId, Long userId) {
        List<Problem> problems = problemRepository.findByQuestIdOrderByOrderIndexAsc(questId);

        if (!RoleUtil.isStaff()) {
            Map<Long, NodeStatus> statuses = questMapService.getStatuses(questId, userId);
            problems = problems.stream()
                    .filter(p -> statuses.get(p.getId()) != NodeStatus.LOCKED)
                    .toList();
        }

        return problems.stream().map(p -> {
            ProblemAttempt attempt = userId != null
                    ? attemptRepository.findByUserIdAndProblemId(userId, p.getId()).orElse(null)
                    : null;
            return mapToProblemResponse(p, attempt);
        }).toList();
    }

    @Override
    @Transactional
    public AnswerResultResponse submitAnswer(SubmitAnswerRequest request, Long userId) {
        Problem problem = problemRepository.findById(request.getProblemId())
                .orElseThrow(() -> new ResourceNotFoundException("Problem", request.getProblemId()));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Quest quest = problem.getQuest();

        // Map rule: the node must be reachable (also covers quest-level prerequisites)
        NodeStatus nodeStatus = questMapService.getStatuses(quest.getId(), userId).get(problem.getId());
        if (nodeStatus == NodeStatus.LOCKED) {
            throw new BadRequestException("This problem is locked. Solve a neighbouring problem on the map first.");
        }

        ProblemAttempt attempt = attemptRepository
                .findByUserIdAndProblemId(userId, problem.getId())
                .orElseGet(() -> ProblemAttempt.builder()
                        .user(user).problem(problem)
                        .attemptsUsed(0).solved(false).answerRevealed(false).xpEarned(0)
                        .build());

        if (attempt.getSolved()) {
            throw new BadRequestException("You have already solved this problem");
        }

        boolean correct = checkAnswer(problem, request);
        attempt.setAttemptsUsed(attempt.getAttemptsUsed() + 1);   // just a counter now, no limit
        attempt.setLastAnswerGiven(getAnswerText(request));
        attempt.setLastAttemptAt(LocalDateTime.now());

        int xpEarned = 0;
        if (correct) {
            attempt.setSolved(true);
            xpEarned = problem.getXpReward();
            attempt.setXpEarned(xpEarned);
        }
        attemptRepository.save(attempt);

        boolean questCompleted = correct && updateQuestProgress(user, quest, xpEarned);

        return AnswerResultResponse.builder()
                .correct(correct)
                .attemptsUsed(attempt.getAttemptsUsed())
                .attemptsRemaining(null)          // unlimited
                .answerRevealed(false)
                .correctAnswer(null)
                .explanation(correct ? problem.getExplanation() : null)
                .explanationImagePath(correct ? problem.getExplanationImagePath() : null)
                .xpEarned(xpEarned)
                .questCompleted(questCompleted)
                .retryAvailableAt(null)
                .build();
    }

    @Override
    @Transactional
    public void deleteProblem(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem", id));
        Quest quest = problem.getQuest();

        problemRepository.delete(problem);   // links are removed by ON DELETE CASCADE
        problemRepository.flush();

        if (Boolean.TRUE.equals(quest.getPublished())) {
            try {
                questMapService.assertMapValid(quest.getId());
            } catch (BadRequestException e) {
                quest.setPublished(false);
                questRepository.save(quest);
                log.warn("Quest {} unpublished after deleting problem {}: {}",
                        quest.getId(), id, e.getMessage());
            }
        }
        log.info("Problem deleted: {}", id);
    }

    // ===== PRIVATE HELPERS =====

    private boolean checkAnswer(Problem problem, SubmitAnswerRequest request) {
        return switch (problem.getProblemType()) {
            case MULTIPLE_CHOICE -> {
                if (request.getSelectedOptionId() == null) {
                    throw new BadRequestException("Please select an answer option");
                }
                AnswerOption selected = answerOptionRepository
                        .findById(request.getSelectedOptionId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Answer option", request.getSelectedOptionId()));
                if (!selected.getProblem().getId().equals(problem.getId())) {
                    throw new BadRequestException("That option does not belong to this problem");
                }
                yield selected.getIsCorrect();
            }
            case OPEN_ANSWER -> {
                if (request.getOpenAnswer() == null || request.getOpenAnswer().isBlank()) {
                    throw new BadRequestException("Please provide an answer");
                }
                yield problem.getCorrectAnswer().trim()
                        .equalsIgnoreCase(request.getOpenAnswer().trim());
            }
        };
    }

    private String getAnswerText(SubmitAnswerRequest request) {
        if (request.getOpenAnswer() != null) return request.getOpenAnswer();
        if (request.getSelectedOptionId() != null) return String.valueOf(request.getSelectedOptionId());
        return null;
    }

    private boolean updateQuestProgress(User user, Quest quest, int xpGained) {
        int totalProblems = problemRepository.countByQuestId(quest.getId());

        UserQuestProgress progress = progressRepository
                .findByUserIdAndQuestId(user.getId(), quest.getId())
                .orElseGet(() -> UserQuestProgress.builder()
                        .user(user).quest(quest)
                        .problemsSolved(0).xpEarned(0)
                        .build());

        progress.setTotalProblems(totalProblems);
        progress.setProblemsSolved(progress.getProblemsSolved() + 1);
        progress.setXpEarned(progress.getXpEarned() + xpGained);
        progress.setStatus(QuestStatus.IN_PROGRESS);
        if (progress.getStartedAt() == null) {
            progress.setStartedAt(LocalDateTime.now());
        }

        boolean questCompleted = progress.getProblemsSolved() >= totalProblems;
        if (questCompleted) {
            progress.setStatus(QuestStatus.COMPLETED);
            progress.setCompletedAt(LocalDateTime.now());
            log.info("Quest {} completed by user {}", quest.getId(), user.getId());
        }
        progressRepository.save(progress);

        if (questCompleted) {
            unlockDependents(user, quest);
        }
        return questCompleted;
    }

    private void unlockDependents(User user, Quest completedQuest) {
        List<Long> completedIds = new ArrayList<>(
                progressRepository.findCompletedQuestIdsByUserId(user.getId()));
        if (!completedIds.contains(completedQuest.getId())) {
            completedIds.add(completedQuest.getId());
        }
        for (Quest dependent : completedQuest.getDependents()) {
            if (!Boolean.TRUE.equals(dependent.getPublished())) continue;
            if (progressRepository.existsByUserIdAndQuestId(user.getId(), dependent.getId())) continue;
            if (!dependent.isUnlocked(completedIds)) continue;

            progressRepository.save(UserQuestProgress.builder()
                    .user(user).quest(dependent)
                    .status(QuestStatus.AVAILABLE)
                    .problemsSolved(0)
                    .totalProblems(problemRepository.countByQuestId(dependent.getId()))
                    .xpEarned(0)
                    .build());
        }
    }

    private ProblemResponse mapToProblemResponse(Problem problem, ProblemAttempt attempt) {
        List<AnswerOption> options = answerOptionRepository
                .findByProblemIdOrderByOrderIndexAsc(problem.getId());

        boolean solved = attempt != null && attempt.getSolved();
        boolean staff = RoleUtil.isStaff();
        boolean reveal = solved || staff;

        List<AnswerOptionResponse> optionResponses = options.stream()
                .map(opt -> AnswerOptionResponse.builder()
                        .id(opt.getId())
                        .optionText(opt.getOptionText())
                        .optionImagePath(opt.getOptionImagePath())
                        .orderIndex(opt.getOrderIndex())
                        .isCorrect(staff ? opt.getIsCorrect() : null)
                        .build())
                .toList();

        Category cat = problem.getCategory();
        Category main = (cat != null && cat.getParent() != null) ? cat.getParent() : cat;

        return ProblemResponse.builder()
                .id(problem.getId())
                .questionText(problem.getQuestionText())
                .questionImagePath(problem.getQuestionImagePath())
                .problemType(problem.getProblemType())
                .difficultyLevel(problem.getDifficultyLevel())
                .orderIndex(problem.getOrderIndex())
                .maxAttempts(problem.getMaxAttempts())
                .xpReward(problem.getXpReward())
                .questId(problem.getQuest().getId())
                .categoryId(cat != null ? cat.getId() : null)
                .categoryName(cat != null ? cat.getName() : null)
                .mainCategoryId(main != null ? main.getId() : null)
                .mainCategoryName(main != null ? main.getName() : null)
                .answerOptions(optionResponses)
                .explanation(reveal ? problem.getExplanation() : null)
                .explanationImagePath(reveal ? problem.getExplanationImagePath() : null)
                .correctAnswer(reveal ? problem.getCorrectAnswer() : null)
                .attemptsUsed(questMapService.effectiveAttemptsUsed(attempt))
                .solved(solved)
                .answerRevealed(false)
                .retryAvailableAt(questMapService.retryAvailableAt(attempt))
                .build();
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }

    private void validateContent(ProblemType type, String correctAnswer, List<Boolean> optionCorrectFlags) {
        if (type == ProblemType.MULTIPLE_CHOICE) {
            if (optionCorrectFlags.size() < 2) {
                throw new BadRequestException("A multiple-choice problem needs at least 2 options");
            }
            if (optionCorrectFlags.stream().filter(Boolean::booleanValue).count() != 1) {
                throw new BadRequestException("A multiple-choice problem needs exactly one correct option");
            }
        } else if (correctAnswer == null || correctAnswer.isBlank()) {
            throw new BadRequestException("An open-answer problem needs a correct answer");
        }
    }
}
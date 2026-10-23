package com.mathweb.service.impl;

import com.mathweb.dto.request.CreateProblemRequest;
import com.mathweb.dto.request.SubmitAnswerRequest;
import com.mathweb.dto.request.UpdateProblemRequest;
import com.mathweb.dto.response.AnswerOptionResponse;
import com.mathweb.dto.response.AnswerResultResponse;
import com.mathweb.dto.response.ProblemResponse;
import com.mathweb.dto.response.HintResponse;
import com.mathweb.dto.response.SolutionResponse;
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
import com.mathweb.util.AnswerChecker;
import com.mathweb.util.EmbedUtil;
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
    private static final double GIVE_UP_PENALTY_RATE = 0.5;

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
    private final ActivityRecorder activityRecorder;

    public ProblemServiceImpl(ProblemRepository problemRepository,
                              AnswerOptionRepository answerOptionRepository,
                              ProblemAttemptRepository attemptRepository,
                              UserRepository userRepository,
                              QuestRepository questRepository,
                              UserQuestProgressRepository progressRepository,
                              CategoryRepository categoryRepository,
                              FileStorageService fileStorageService,

                              QuestMapService questMapService, ActivityRecorder activityRecorder) {
        this.problemRepository = problemRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.attemptRepository = attemptRepository;
        this.userRepository = userRepository;
        this.questRepository = questRepository;
        this.progressRepository = progressRepository;
        this.categoryRepository = categoryRepository;
        this.fileStorageService = fileStorageService;
        this.questMapService = questMapService;
        this.activityRecorder = activityRecorder;
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
                .questionText(SanitizationUtil.sanitizeMathText(request.getQuestionText()))
                .problemType(request.getProblemType())
                .difficultyLevel(request.getDifficultyLevel())
                .correctAnswer(request.getCorrectAnswer())
                .answerTolerance(request.getAnswerTolerance())
                .explanation(request.getExplanation())
                .hints(cleanHints(request.getHints()))
                .orderIndex(request.getOrderIndex())
                .xpReward(request.getXpReward())
                .category(resolveCategory(request.getCategoryId()))
                .quest(quest)
                .build();
        applyEmbed(problem, request.getEmbedUrl());
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

        problem.setQuestionText(SanitizationUtil.sanitizeMathText(request.getQuestionText()));
        problem.setProblemType(request.getProblemType());
        problem.setDifficultyLevel(request.getDifficultyLevel());
        problem.setCorrectAnswer(request.getCorrectAnswer());
        problem.setAnswerTolerance(request.getAnswerTolerance());
        problem.setExplanation(request.getExplanation());
        problem.setHints(cleanHints(request.getHints()));
        applyEmbed(problem, request.getEmbedUrl());
        problem.setOrderIndex(request.getOrderIndex());
        problem.setXpReward(request.getXpReward());
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
            xpEarned = Boolean.TRUE.equals(attempt.getAnswerRevealed()) ? 0 : problem.getXpReward();
            attempt.setXpEarned(xpEarned);
        }
        attemptRepository.save(attempt);
        activityRecorder.record(userId);

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

    @Override
    @Transactional
    public HintResponse revealHint(Long problemId, Long userId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ResourceNotFoundException("Problem", problemId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        assertReachable(problem, userId);

        ProblemAttempt attempt = findOrCreateAttempt(user, problem);
        if (Boolean.TRUE.equals(attempt.getSolved())) {
            throw new BadRequestException("You have already solved this problem");
        }

        List<String> all = problem.getHints() == null ? List.of() : problem.getHints();
        int used = attempt.getHintsUsed() == null ? 0 : attempt.getHintsUsed();

        if (used < all.size()) {          // reveal the next one only if there is one
            used++;
            attempt.setHintsUsed(used);
            attemptRepository.save(attempt);
        }

        return HintResponse.builder()
                .hints(new ArrayList<>(all.subList(0, used)))
                .hintsUsed(used)
                .hintCount(all.size())
                .build();
    }

    @Override
    @Transactional
    public SolutionResponse giveUp(Long problemId, Long userId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ResourceNotFoundException("Problem", problemId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        assertReachable(problem, userId);

        ProblemAttempt attempt = findOrCreateAttempt(user, problem);
        boolean alreadyRevealed = Boolean.TRUE.equals(attempt.getAnswerRevealed());
        boolean solved = Boolean.TRUE.equals(attempt.getSolved());
        if (solved && !alreadyRevealed) {
            throw new BadRequestException("You have already solved this problem");
        }

        int penalty = 0;
        boolean questCompleted = false;
        if (!solved) {
            if (!alreadyRevealed) {                    // charge only the first time
                penalty = applyGiveUpPenalty(user, problem);
            }
            attempt.setAnswerRevealed(true);
            attempt.setSolved(true);                   // counts as done for the map
            attempt.setXpEarned(0);
            attemptRepository.save(attempt);
            activityRecorder.record(userId);
            questCompleted = updateQuestProgress(user, problem.getQuest(), 0);
        }

        AnswerOption correctOption = problem.getProblemType() == ProblemType.MULTIPLE_CHOICE
                ? findCorrectOption(problem) : null;

        return SolutionResponse.builder()
                .correctAnswer(correctOption != null ? correctOption.getOptionText()
                        : problem.getCorrectAnswer())
                .correctOptionId(correctOption != null ? correctOption.getId() : null)
                .explanation(problem.getExplanation())
                .explanationImagePath(problem.getExplanationImagePath())
                .xpPenalty(penalty)
                .questCompleted(questCompleted)
                .build();
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
                String given = request.getOpenAnswer();
                if (given == null || given.isBlank()) {
                    throw new BadRequestException("Please provide an answer");
                }
                yield AnswerChecker.checkText(problem.getCorrectAnswer(), given);
            }
            case NUMERIC -> {
                String given = request.getOpenAnswer();
                if (given == null || given.isBlank()) {
                    throw new BadRequestException("Please provide an answer");
                }
                if (AnswerChecker.parseNumber(given).isEmpty()) {
                    throw new BadRequestException("Enter a number, for example 3, -2.5 or 1/2");
                }
                yield AnswerChecker.checkNumeric(problem.getCorrectAnswer(), given,
                        problem.getAnswerTolerance());
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

        boolean solved = attempt != null && Boolean.TRUE.equals(attempt.getSolved());
        boolean staff = RoleUtil.isStaff();
        boolean revealed = attempt != null && Boolean.TRUE.equals(attempt.getAnswerRevealed());
        boolean reveal = solved || staff || revealed;

        List<String> allHints = problem.getHints() == null ? List.of() : problem.getHints();
        int hintsUsed = attempt != null && attempt.getHintsUsed() != null ? attempt.getHintsUsed() : 0;
        List<String> visibleHints = staff
                ? allHints
                : new ArrayList<>(allHints.subList(0, Math.min(hintsUsed, allHints.size())));

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

        AnswerOption correctOption = (reveal && problem.getProblemType() == ProblemType.MULTIPLE_CHOICE)
                ? options.stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).findFirst().orElse(null)
                : null;

        return ProblemResponse.builder()
                .id(problem.getId())
                .questionText(problem.getQuestionText())
                .questionImagePath(problem.getQuestionImagePath())
                .problemType(problem.getProblemType())
                .difficultyLevel(problem.getDifficultyLevel())
                .orderIndex(problem.getOrderIndex())
                .xpReward(problem.getXpReward())
                .questId(problem.getQuest().getId())
                .categoryId(cat != null ? cat.getId() : null)
                .categoryName(cat != null ? cat.getName() : null)
                .mainCategoryId(main != null ? main.getId() : null)
                .mainCategoryName(main != null ? main.getName() : null)
                .answerOptions(optionResponses)
                .explanation(reveal ? problem.getExplanation() : null)
                .explanationImagePath(reveal ? problem.getExplanationImagePath() : null)
                .correctAnswer(reveal
                        ? (correctOption != null ? correctOption.getOptionText() : problem.getCorrectAnswer())
                        : null)
                .correctOptionId(correctOption != null ? correctOption.getId() : null)
                .answerTolerance(staff ? problem.getAnswerTolerance() : null)
                .hintCount(allHints.size())
                .hints(visibleHints)
                .hintsUsed(hintsUsed)
                .embedType(problem.getEmbedType())
                .embedRef(problem.getEmbedRef())
                .embedUrl(EmbedUtil.iframeUrl(problem.getEmbedType(), problem.getEmbedRef()))
                .attemptsUsed(questMapService.effectiveAttemptsUsed(attempt))
                .solved(solved)
                .answerRevealed(revealed)
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
            return;
        }

        if (correctAnswer == null || correctAnswer.isBlank()) {
            throw new BadRequestException("This problem needs a correct answer");
        }
        if (type == ProblemType.NUMERIC) {
            for (String accepted : AnswerChecker.acceptedAnswers(correctAnswer)) {
                if (AnswerChecker.parseNumber(accepted).isEmpty()) {
                    throw new BadRequestException(
                            "'" + accepted + "' is not a valid number (use 3, -2.5 or 1/2)");
                }
            }
        }
    }

    private List<String> cleanHints(List<String> raw) {
        if (raw == null) return new ArrayList<>();
        return raw.stream()
                .filter(h -> h != null && !h.isBlank())
                .map(SanitizationUtil::sanitizeMathText)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private void applyEmbed(Problem problem, String link) {
        if (link == null || link.isBlank()) {
            problem.setEmbedType(null);
            problem.setEmbedRef(null);
            return;
        }
        EmbedUtil.Embed embed = EmbedUtil.parse(link).orElseThrow(() ->
                new BadRequestException(
                        "Paste a Desmos (desmos.com/calculator/...) or GeoGebra (geogebra.org/m/...) link"));
        problem.setEmbedType(embed.type());
        problem.setEmbedRef(embed.ref());
    }

    private void assertReachable(Problem problem, Long userId) {
        NodeStatus status = questMapService
                .getStatuses(problem.getQuest().getId(), userId).get(problem.getId());
        if (status == NodeStatus.LOCKED) {
            throw new BadRequestException("This problem is locked. Solve a neighbouring problem on the map first.");
        }
    }

    private AnswerOption findCorrectOption(Problem problem) {
        return answerOptionRepository.findByProblemIdOrderByOrderIndexAsc(problem.getId())
                .stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                .findFirst()
                .orElse(null);
    }

    private int applyGiveUpPenalty(User user, Problem problem) {
        int reward = problem.getXpReward() == null ? 0 : problem.getXpReward();
        if (reward <= 0) return 0;
        int penalty = Math.max(1, (int) Math.round(reward * GIVE_UP_PENALTY_RATE));

        UserQuestProgress progress = progressRepository
                .findByUserIdAndQuestId(user.getId(), problem.getQuest().getId())
                .orElse(null);
        if (progress == null) return 0;

        int current = progress.getXpEarned() == null ? 0 : progress.getXpEarned();
        int charged = Math.min(penalty, current);
        progress.setXpEarned(current - charged);
        progressRepository.save(progress);
        return charged;
    }

    private ProblemAttempt findOrCreateAttempt(User user, Problem problem) {
        return attemptRepository.findByUserIdAndProblemId(user.getId(), problem.getId())
                .orElseGet(() -> ProblemAttempt.builder()
                        .user(user).problem(problem)
                        .attemptsUsed(0).solved(false).answerRevealed(false).xpEarned(0)
                        .build());
    }
}
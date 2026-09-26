package com.mathweb.service.impl;

import com.mathweb.dto.request.CreateProblemRequest;
import com.mathweb.dto.request.SubmitAnswerRequest;
import com.mathweb.dto.response.AnswerOptionResponse;
import com.mathweb.dto.response.AnswerResultResponse;
import com.mathweb.dto.response.ProblemResponse;
import com.mathweb.entity.*;
import com.mathweb.enums.QuestStatus;
import com.mathweb.exception.BadRequestException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.*;
import com.mathweb.service.ProblemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProblemServiceImpl implements ProblemService {

    private static final Logger log = LoggerFactory.getLogger(ProblemServiceImpl.class);

    private final ProblemRepository problemRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final ProblemAttemptRepository attemptRepository;
    private final UserRepository userRepository;
    private final QuestRepository questRepository;
    private final UserQuestProgressRepository progressRepository;

    public ProblemServiceImpl(ProblemRepository problemRepository,
                              AnswerOptionRepository answerOptionRepository,
                              ProblemAttemptRepository attemptRepository,
                              UserRepository userRepository,
                              QuestRepository questRepository,
                              UserQuestProgressRepository progressRepository) {
        this.problemRepository = problemRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.attemptRepository = attemptRepository;
        this.userRepository = userRepository;
        this.questRepository = questRepository;
        this.progressRepository = progressRepository;
    }

    @Override
    @Transactional
    public ProblemResponse createProblem(CreateProblemRequest request) {
        Quest quest = questRepository.findById(request.getQuestId())
                .orElseThrow(() -> new ResourceNotFoundException("Quest", request.getQuestId()));

        Problem problem = Problem.builder()
                .questionText(request.getQuestionText())
                .problemType(request.getProblemType())
                .difficultyLevel(request.getDifficultyLevel())
                .correctAnswer(request.getCorrectAnswer())
                .explanation(request.getExplanation())
                .orderIndex(request.getOrderIndex())
                .xpReward(request.getXpReward())
                .maxAttempts(3)
                .quest(quest)
                .build();

        problemRepository.save(problem);

        // Save answer options for MULTIPLE_CHOICE
        if (request.getAnswerOptions() != null) {
            for (int i = 0; i < request.getAnswerOptions().size(); i++) {
                CreateProblemRequest.AnswerOptionRequest opt = request.getAnswerOptions().get(i);
                AnswerOption option = AnswerOption.builder()
                        .problem(problem)
                        .optionText(opt.getOptionText())
                        .isCorrect(opt.getIsCorrect())
                        .orderIndex(i)
                        .build();
                answerOptionRepository.save(option);
            }
        }

        log.info("Problem created in quest {}", quest.getId());
        return mapToProblemResponse(problem, null);
    }

    @Override
    @Transactional(readOnly = true)
    public ProblemResponse getProblemById(Long id, Long userId) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem", id));

        ProblemAttempt attempt = userId != null
                ? attemptRepository.findByUserIdAndProblemId(userId, id).orElse(null)
                : null;

        return mapToProblemResponse(problem, attempt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProblemResponse> getProblemsByQuest(Long questId, Long userId) {
        List<Problem> problems = problemRepository.findByQuestIdOrderByOrderIndexAsc(questId);

        return problems.stream()
                .map(p -> {
                    ProblemAttempt attempt = userId != null
                            ? attemptRepository.findByUserIdAndProblemId(userId, p.getId()).orElse(null)
                            : null;
                    return mapToProblemResponse(p, attempt);
                })
                .toList();
    }

    @Override
    @Transactional
    public AnswerResultResponse submitAnswer(SubmitAnswerRequest request, Long userId) {
        Problem problem = problemRepository.findById(request.getProblemId())
                .orElseThrow(() -> new ResourceNotFoundException("Problem", request.getProblemId()));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // Get or create attempt record
        ProblemAttempt attempt = attemptRepository
                .findByUserIdAndProblemId(userId, problem.getId())
                .orElseGet(() -> ProblemAttempt.builder()
                        .user(user)
                        .problem(problem)
                        .attemptsUsed(0)
                        .solved(false)
                        .answerRevealed(false)
                        .xpEarned(0)
                        .build());

        if (attempt.getSolved()) {
            throw new BadRequestException("You have already solved this problem");
        }

        if (!attempt.canAttempt()) {
            throw new BadRequestException("No attempts remaining for this problem");
        }

        // Check the answer
        boolean correct = checkAnswer(problem, request);
        attempt.setAttemptsUsed(attempt.getAttemptsUsed() + 1);
        attempt.setLastAnswerGiven(getAnswerText(request));

        int attemptsRemaining = problem.getMaxAttempts() - attempt.getAttemptsUsed();
        boolean answerRevealed = false;
        int xpEarned = 0;
        boolean questCompleted = false;

        if (correct) {
            attempt.setSolved(true);
            xpEarned = problem.getXpReward();
            attempt.setXpEarned(xpEarned);

            // Update quest progress
            questCompleted = updateQuestProgress(user, problem.getQuest());
        } else if (attemptsRemaining <= 0) {
            // Exhausted all attempts — reveal answer
            attempt.setAnswerRevealed(true);
            answerRevealed = true;
        }

        attemptRepository.save(attempt);

        return AnswerResultResponse.builder()
                .correct(correct)
                .attemptsUsed(attempt.getAttemptsUsed())
                .attemptsRemaining(Math.max(0, attemptsRemaining))
                .answerRevealed(answerRevealed)
                .correctAnswer(answerRevealed ? problem.getCorrectAnswer() : null)
                .explanation(answerRevealed || correct ? problem.getExplanation() : null)
                .explanationImagePath(answerRevealed || correct ? problem.getExplanationImagePath() : null)
                .xpEarned(xpEarned)
                .questCompleted(questCompleted)
                .build();
    }

    @Override
    @Transactional
    public void deleteProblem(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem", id));
        problemRepository.delete(problem);
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

    private boolean updateQuestProgress(User user, Quest quest) {
        UserQuestProgress progress = progressRepository
                .findByUserIdAndQuestId(user.getId(), quest.getId())
                .orElseGet(() -> UserQuestProgress.builder()
                        .user(user)
                        .quest(quest)
                        .status(QuestStatus.IN_PROGRESS)
                        .problemsSolved(0)
                        .totalProblems(problemRepository.countByQuestId(quest.getId()))
                        .xpEarned(0)
                        .startedAt(LocalDateTime.now())
                        .build());

        progress.setProblemsSolved(progress.getProblemsSolved() + 1);
        progress.setXpEarned(progress.getXpEarned() +
                attemptRepository.findByUserIdAndQuestId(user.getId(), quest.getId())
                        .stream().mapToInt(ProblemAttempt::getXpEarned).sum());
        progress.setStatus(QuestStatus.IN_PROGRESS);

        boolean questCompleted = false;
        if (progress.getProblemsSolved() >= progress.getTotalProblems()) {
            progress.setStatus(QuestStatus.COMPLETED);
            progress.setCompletedAt(LocalDateTime.now());
            questCompleted = true;
            log.info("Quest {} completed by user {}", quest.getId(), user.getId());
        }

        progressRepository.save(progress);
        return questCompleted;
    }

    private ProblemResponse mapToProblemResponse(Problem problem, ProblemAttempt attempt) {
        List<AnswerOption> options = answerOptionRepository
                .findByProblemIdOrderByOrderIndexAsc(problem.getId());

        boolean revealAnswer = attempt != null &&
                (attempt.getSolved() || attempt.getAnswerRevealed());

        List<AnswerOptionResponse> optionResponses = options.stream()
                .map(opt -> AnswerOptionResponse.builder()
                        .id(opt.getId())
                        .optionText(opt.getOptionText())
                        .optionImagePath(opt.getOptionImagePath())
                        .orderIndex(opt.getOrderIndex())
                        .build())
                .toList();

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
                .answerOptions(optionResponses)
                // Only reveal answer/explanation after attempts exhausted or solved
                .explanation(revealAnswer ? problem.getExplanation() : null)
                .explanationImagePath(revealAnswer ? problem.getExplanationImagePath() : null)
                .correctAnswer(revealAnswer ? problem.getCorrectAnswer() : null)
                .attemptsUsed(attempt != null ? attempt.getAttemptsUsed() : 0)
                .solved(attempt != null && attempt.getSolved())
                .answerRevealed(attempt != null && attempt.getAnswerRevealed())
                .build();
    }
}
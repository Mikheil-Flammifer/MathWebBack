package com.mathweb.service.impl;

import com.mathweb.dto.response.TopicProgressResponse;
import com.mathweb.dto.response.MistakeResponse;
import com.mathweb.entity.Problem;
import java.time.LocalDateTime;
import com.mathweb.entity.Category;
import com.mathweb.entity.ProblemAttempt;
import com.mathweb.repository.CategoryRepository;
import com.mathweb.repository.ProblemAttemptRepository;
import com.mathweb.repository.ProblemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ProgressService {

    private static final double MASTERY_PERCENT = 80.0;

    private final ProblemRepository problemRepository;
    private final ProblemAttemptRepository attemptRepository;
    private final CategoryRepository categoryRepository;

    public ProgressService(ProblemRepository problemRepository,
                           ProblemAttemptRepository attemptRepository,
                           CategoryRepository categoryRepository) {
        this.problemRepository = problemRepository;
        this.attemptRepository = attemptRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<TopicProgressResponse> getTopicProgress(Long userId) {
        // stats per category id: [total, solved, firstTry]
        Map<Long, long[]> stats = new HashMap<>();
        for (Object[] row : problemRepository.countPublishedByCategory()) {
            stats.computeIfAbsent(((Number) row[0]).longValue(), k -> new long[3])[0] =
                    ((Number) row[1]).longValue();
        }
        for (ProblemAttempt a : attemptRepository.findForProgress(userId)) {
            if (!Boolean.TRUE.equals(a.getSolved())
                    || Boolean.TRUE.equals(a.getAnswerRevealed())) continue;   // given-up problems don't count as mastered
            long[] s = stats.computeIfAbsent(a.getProblem().getCategory().getId(), k -> new long[3]);
            s[1]++;
            if (isFirstTry(a)) s[2]++;
        }

        List<Category> all = categoryRepository.findAllByOrderByOrderIndexAsc();
        Map<Long, List<Category>> children = new HashMap<>();
        for (Category c : all) {
            if (c.getParent() != null) {
                children.computeIfAbsent(c.getParent().getId(), k -> new ArrayList<>()).add(c);
            }
        }

        List<TopicProgressResponse> result = new ArrayList<>();
        for (Category main : all) {
            if (main.getParent() != null) continue;

            long[] sum = stats.getOrDefault(main.getId(), new long[3]).clone();
            List<TopicProgressResponse> subs = new ArrayList<>();
            for (Category sub : children.getOrDefault(main.getId(), List.of())) {
                long[] s = stats.getOrDefault(sub.getId(), new long[3]);
                for (int i = 0; i < 3; i++) sum[i] += s[i];
                subs.add(build(sub, s, List.of()));
            }
            result.add(build(main, sum, subs));
        }
        return result;
    }

    private boolean isFirstTry(ProblemAttempt a) {
        return a.getAttemptsUsed() != null && a.getAttemptsUsed() == 1
                && (a.getHintsUsed() == null || a.getHintsUsed() == 0)
                && !Boolean.TRUE.equals(a.getAnswerRevealed());
    }

    private TopicProgressResponse build(Category c, long[] s, List<TopicProgressResponse> subs) {
        long total = s[0], solved = s[1], firstTry = s[2];
        double percent = total == 0 ? 0.0 : Math.round(solved * 1000.0 / total) / 10.0;

        String level;
        if (solved == 0) level = "NOT_STARTED";
        else if (percent >= MASTERY_PERCENT && firstTry * 2 >= solved) level = "MASTERED";
        else level = "LEARNING";

        return TopicProgressResponse.builder()
                .categoryId(c.getId())
                .name(c.getName())
                .slug(c.getSlug())
                .icon(c.getIcon())
                .color(c.getColor())
                .totalProblems((int) total)
                .solved((int) solved)
                .firstTrySolved((int) firstTry)
                .percent(percent)
                .level(level)
                .subtopics(subs)
                .build();
    }

    @Transactional(readOnly = true)
    public List<MistakeResponse> getMistakes(Long userId, Long categoryId) {
        List<MistakeResponse> result = new ArrayList<>();

        for (ProblemAttempt a : attemptRepository.findForProgress(userId)) {
            Problem p = a.getProblem();
            Category cat = p.getCategory();
            Category main = cat.getParent() != null ? cat.getParent() : cat;

            if (categoryId != null
                    && !categoryId.equals(cat.getId())
                    && !categoryId.equals(main.getId())) continue;

            String reason = mistakeReason(a);
            if (reason == null) continue;

            result.add(MistakeResponse.builder()
                    .problemId(p.getId())
                    .questId(p.getQuest().getId())
                    .questionText(p.getQuestionText())
                    .categoryId(cat.getId())
                    .categoryName(cat.getName())
                    .mainCategoryId(main.getId())
                    .mainCategoryName(main.getName())
                    .reason(reason)
                    .solved(Boolean.TRUE.equals(a.getSolved()))
                    .attemptsUsed(a.getAttemptsUsed())
                    .hintsUsed(a.getHintsUsed())
                    .lastAttemptAt(a.getLastAttemptAt())
                    .build());
        }

        result.sort(Comparator
                .comparingInt((MistakeResponse m) -> reasonRank(m.getReason()))
                .thenComparing(MistakeResponse::getLastAttemptAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private String mistakeReason(ProblemAttempt a) {
        boolean solved = Boolean.TRUE.equals(a.getSolved());
        int attempts = a.getAttemptsUsed() == null ? 0 : a.getAttemptsUsed();
        int hints = a.getHintsUsed() == null ? 0 : a.getHintsUsed();

        if (Boolean.TRUE.equals(a.getAnswerRevealed())) return "GAVE_UP";
        if (!solved) return attempts >= 1 ? "UNSOLVED_WRONG" : null;
        if (hints > 0) return "SOLVED_WITH_HINTS";
        if (attempts > 1) return "SOLVED_AFTER_RETRIES";
        return null;
    }

    private int reasonRank(String reason) {
        return switch (reason) {
            case "GAVE_UP" -> 0;
            case "UNSOLVED_WRONG" -> 1;
            case "SOLVED_WITH_HINTS" -> 2;
            default -> 3;
        };
    }
}
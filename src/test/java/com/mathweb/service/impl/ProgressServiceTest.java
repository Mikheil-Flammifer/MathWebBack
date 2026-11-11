package com.mathweb.service.impl;

import com.mathweb.dto.response.MistakeResponse;
import com.mathweb.dto.response.TopicProgressResponse;
import com.mathweb.entity.Category;
import com.mathweb.entity.Problem;
import com.mathweb.entity.ProblemAttempt;
import com.mathweb.entity.Quest;
import com.mathweb.repository.CategoryRepository;
import com.mathweb.repository.ProblemAttemptRepository;
import com.mathweb.repository.ProblemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Unit tests for {@link ProgressService}. */
@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private ProblemRepository problemRepository;
    @Mock private ProblemAttemptRepository attemptRepository;
    @Mock private CategoryRepository categoryRepository;

    @InjectMocks private ProgressService service;

    private Category algebra;      // main
    private Category equations;   // sub of algebra
    private Category geometry;     // main, no subs

    @BeforeEach
    void setUp() {
        algebra = category(1L, "Algebra", null);
        equations = category(2L, "Equations", algebra);
        geometry = category(3L, "Geometry", null);
    }

    private Category category(Long id, String name, Category parent) {
        Category c = Category.builder().name(name).slug(name.toLowerCase()).parent(parent).build();
        c.setId(id);
        return c;
    }

    private Problem problem(Long id, Category category) {
        Quest quest = Quest.builder().build();
        quest.setId(100L + id);
        Problem p = Problem.builder().questionText("Q" + id).category(category).quest(quest).build();
        p.setId(id);
        return p;
    }

    private ProblemAttempt attempt(Problem p, boolean solved, int attempts, int hints, boolean revealed) {
        return ProblemAttempt.builder().problem(p).solved(solved).attemptsUsed(attempts)
                .hintsUsed(hints).answerRevealed(revealed).lastAttemptAt(LocalDateTime.now()).build();
    }

    private void givenCategories() {
        when(categoryRepository.findAllByOrderByOrderIndexAsc()).thenReturn(List.of(algebra, equations, geometry));
    }

    private void givenTotals(Object[]... rows) {
        when(problemRepository.countPublishedByCategory()).thenReturn(new ArrayList<>(List.of(rows)));
    }

    @Nested
    class TopicProgress {

        @Test
        void returnsOnlyMainCategories_withSubtopicsNested() {
            givenCategories();
            givenTotals();
            when(attemptRepository.findForProgress(USER_ID)).thenReturn(List.of());

            List<TopicProgressResponse> result = service.getTopicProgress(USER_ID);

            assertThat(result).extracting(TopicProgressResponse::getName).containsExactly("Algebra", "Geometry");
            assertThat(result.get(0).getSubtopics()).extracting(TopicProgressResponse::getName)
                    .containsExactly("Equations");
            assertThat(result.get(1).getSubtopics()).isEmpty();
        }

        @Test
        void noAttempts_isNotStartedWithZeroPercent() {
            givenCategories();
            givenTotals(new Object[]{3L, 5L});
            when(attemptRepository.findForProgress(USER_ID)).thenReturn(List.of());

            TopicProgressResponse geo = service.getTopicProgress(USER_ID).get(1);

            assertThat(geo.getTotalProblems()).isEqualTo(5);
            assertThat(geo.getSolved()).isZero();
            assertThat(geo.getPercent()).isZero();
            assertThat(geo.getLevel()).isEqualTo("NOT_STARTED");
        }

        @Test
        void categoryWithoutProblems_hasZeroPercent_notNaN() {
            givenCategories();
            givenTotals();
            when(attemptRepository.findForProgress(USER_ID)).thenReturn(List.of());

            assertThat(service.getTopicProgress(USER_ID).get(1).getPercent()).isZero();
        }

        @Test
        void mainCategoryTotals_includeItsSubcategories() {
            Problem inMain = problem(10L, algebra);
            Problem inSub = problem(11L, equations);
            givenCategories();
            givenTotals(new Object[]{1L, 2L}, new Object[]{2L, 2L});
            when(attemptRepository.findForProgress(USER_ID)).thenReturn(List.of(
                    attempt(inMain, true, 1, 0, false),
                    attempt(inSub, true, 1, 0, false)));

            TopicProgressResponse main = service.getTopicProgress(USER_ID).get(0);

            assertThat(main.getTotalProblems()).isEqualTo(4);
            assertThat(main.getSolved()).isEqualTo(2);
            assertThat(main.getPercent()).isEqualTo(50.0);
            assertThat(main.getSubtopics().get(0).getSolved()).isEqualTo(1);
            assertThat(main.getSubtopics().get(0).getTotalProblems()).isEqualTo(2);
        }

        @Test
        void highPercentWithMostlyFirstTrySolves_isMastered() {
            Problem p1 = problem(10L, geometry);
            Problem p2 = problem(11L, geometry);
            givenCategories();
            givenTotals(new Object[]{3L, 2L});
            when(attemptRepository.findForProgress(USER_ID)).thenReturn(List.of(
                    attempt(p1, true, 1, 0, false),
                    attempt(p2, true, 1, 0, false)));

            TopicProgressResponse geo = service.getTopicProgress(USER_ID).get(1);

            assertThat(geo.getPercent()).isEqualTo(100.0);
            assertThat(geo.getFirstTrySolved()).isEqualTo(2);
            assertThat(geo.getLevel()).isEqualTo("MASTERED");
        }

        @Test
        void highPercentButFewFirstTries_isStillLearning() {
            Problem p1 = problem(10L, geometry);
            Problem p2 = problem(11L, geometry);
            Problem p3 = problem(12L, geometry);
            givenCategories();
            givenTotals(new Object[]{3L, 3L});
            when(attemptRepository.findForProgress(USER_ID)).thenReturn(List.of(
                    attempt(p1, true, 1, 0, false),
                    attempt(p2, true, 3, 0, false),
                    attempt(p3, true, 2, 1, false)));

            TopicProgressResponse geo = service.getTopicProgress(USER_ID).get(1);

            assertThat(geo.getFirstTrySolved()).isEqualTo(1);
            assertThat(geo.getLevel()).isEqualTo("LEARNING");
        }

        @Test
        void lowPercent_isLearning() {
            Problem p1 = problem(10L, geometry);
            givenCategories();
            givenTotals(new Object[]{3L, 4L});
            when(attemptRepository.findForProgress(USER_ID)).thenReturn(List.of(attempt(p1, true, 1, 0, false)));

            TopicProgressResponse geo = service.getTopicProgress(USER_ID).get(1);

            assertThat(geo.getPercent()).isEqualTo(25.0);
            assertThat(geo.getLevel()).isEqualTo("LEARNING");
        }

        @Test
        void unsolvedAndGivenUpAttempts_doNotCountAsSolved() {
            Problem p1 = problem(10L, geometry);
            Problem p2 = problem(11L, geometry);
            givenCategories();
            givenTotals(new Object[]{3L, 2L});
            when(attemptRepository.findForProgress(USER_ID)).thenReturn(List.of(
                    attempt(p1, false, 2, 0, false),
                    attempt(p2, true, 2, 0, true)));

            TopicProgressResponse geo = service.getTopicProgress(USER_ID).get(1);

            assertThat(geo.getSolved()).isZero();
            assertThat(geo.getLevel()).isEqualTo("NOT_STARTED");
        }

        @Test
        void percentIsRoundedToOneDecimal() {
            Problem p1 = problem(10L, geometry);
            givenCategories();
            givenTotals(new Object[]{3L, 3L});
            when(attemptRepository.findForProgress(USER_ID)).thenReturn(List.of(attempt(p1, true, 1, 0, false)));

            assertThat(service.getTopicProgress(USER_ID).get(1).getPercent()).isEqualTo(33.3);
        }
    }

    @Nested
    class Mistakes {

        @Test
        void classifiesEachAttempt_andSkipsCleanOnes() {
            Problem gaveUp = problem(10L, equations);
            Problem wrong = problem(11L, equations);
            Problem hints = problem(12L, equations);
            Problem retries = problem(13L, equations);
            Problem clean = problem(14L, equations);
            Problem untouched = problem(15L, equations);
            when(attemptRepository.findForProgress(USER_ID)).thenReturn(List.of(
                    attempt(gaveUp, false, 3, 0, true),
                    attempt(wrong, false, 2, 0, false),
                    attempt(hints, true, 1, 2, false),
                    attempt(retries, true, 3, 0, false),
                    attempt(clean, true, 1, 0, false),
                    attempt(untouched, false, 0, 0, false)));

            List<MistakeResponse> result = service.getMistakes(USER_ID, null);

            assertThat(result).extracting(MistakeResponse::getProblemId).containsExactly(10L, 11L, 12L, 13L);
            assertThat(result).extracting(MistakeResponse::getReason).containsExactly(
                    "GAVE_UP", "UNSOLVED_WRONG", "SOLVED_WITH_HINTS", "SOLVED_AFTER_RETRIES");
        }

        @Test
        void sortsByReasonRank_thenNewestFirst() {
            Problem older = problem(10L, geometry);
            Problem newer = problem(11L, geometry);
            Problem gaveUp = problem(12L, geometry);
            ProblemAttempt a1 = attempt(older, false, 1, 0, false);
            a1.setLastAttemptAt(LocalDateTime.now().minusDays(2));
            ProblemAttempt a2 = attempt(newer, false, 1, 0, false);
            a2.setLastAttemptAt(LocalDateTime.now().minusDays(1));
            ProblemAttempt a3 = attempt(gaveUp, false, 1, 0, true);
            when(attemptRepository.findForProgress(USER_ID)).thenReturn(List.of(a1, a2, a3));

            List<MistakeResponse> result = service.getMistakes(USER_ID, null);

            assertThat(result).extracting(MistakeResponse::getProblemId).containsExactly(12L, 11L, 10L);
        }

        @Test
        void filtersByCategory_matchingSubOrMainCategoryId() {
            Problem inSub = problem(10L, equations);
            Problem inOther = problem(11L, geometry);
            when(attemptRepository.findForProgress(USER_ID)).thenReturn(List.of(
                    attempt(inSub, false, 1, 0, false),
                    attempt(inOther, false, 1, 0, false)));

            assertThat(service.getMistakes(USER_ID, 2L)).extracting(MistakeResponse::getProblemId).containsExactly(10L);
            assertThat(service.getMistakes(USER_ID, 1L)).extracting(MistakeResponse::getProblemId).containsExactly(10L);
            assertThat(service.getMistakes(USER_ID, 3L)).extracting(MistakeResponse::getProblemId).containsExactly(11L);
            assertThat(service.getMistakes(USER_ID, 999L)).isEmpty();
        }

        @Test
        void mapsCategoryAndQuestDetails_forSubcategoryProblem() {
            Problem p = problem(10L, equations);
            when(attemptRepository.findForProgress(USER_ID)).thenReturn(List.of(attempt(p, false, 2, 1, false)));

            MistakeResponse m = service.getMistakes(USER_ID, null).get(0);

            assertThat(m.getQuestId()).isEqualTo(110L);
            assertThat(m.getQuestionText()).isEqualTo("Q10");
            assertThat(m.getCategoryId()).isEqualTo(2L);
            assertThat(m.getCategoryName()).isEqualTo("Equations");
            assertThat(m.getMainCategoryId()).isEqualTo(1L);
            assertThat(m.getMainCategoryName()).isEqualTo("Algebra");
            assertThat(m.getSolved()).isFalse();
            assertThat(m.getAttemptsUsed()).isEqualTo(2);
            assertThat(m.getHintsUsed()).isEqualTo(1);
        }

        @Test
        void mainCategoryProblem_usesItselfAsMainCategory() {
            Problem p = problem(10L, geometry);
            when(attemptRepository.findForProgress(USER_ID)).thenReturn(List.of(attempt(p, false, 1, 0, false)));

            MistakeResponse m = service.getMistakes(USER_ID, null).get(0);

            assertThat(m.getMainCategoryId()).isEqualTo(3L);
            assertThat(m.getMainCategoryName()).isEqualTo("Geometry");
        }
    }
}
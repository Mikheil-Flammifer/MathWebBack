package com.mathweb.entity;

import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.EmbedType;
import com.mathweb.enums.ProblemType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Problem} entity.
 * Tests field defaults, business logic, and relationships WITHOUT using .id()
 */
class ProblemTest {

    // ==================== DEFAULT VALUES ====================

    @Nested
    class DefaultValues {

        @Test
        void newlyCreatedProblem_hasDefaultOrderIndexZero() {
            Problem problem = Problem.builder().build();
            assertThat(problem.getOrderIndex()).isEqualTo(0);
        }

        @Test
        void newlyCreatedProblem_hasDefaultXpRewardTen() {
            Problem problem = Problem.builder().build();
            assertThat(problem.getXpReward()).isEqualTo(10);
        }

        @Test
        void newlyCreatedProblem_hasDefaultStartNodeFalse() {
            Problem problem = Problem.builder().build();
            assertThat(problem.getStartNode()).isFalse();
        }

        @Test
        void newlyCreatedProblem_hasEmptyHintsList() {
            Problem problem = Problem.builder().build();
            assertThat(problem.getHints()).isNotNull();
            assertThat(problem.getHints()).isEmpty();
        }

        @Test
        void newlyCreatedProblem_hasEmptyAnswerOptionsList() {
            Problem problem = Problem.builder().build();
            assertThat(problem.getAnswerOptions()).isNotNull();
            assertThat(problem.getAnswerOptions()).isEmpty();
        }

        @Test
        void newlyCreatedProblem_hasEmptyAttemptsList() {
            Problem problem = Problem.builder().build();
            assertThat(problem.getAttempts()).isNotNull();
            assertThat(problem.getAttempts()).isEmpty();
        }
    }

    // ==================== BUILDER ====================

    @Nested
    class Builder {

        @Test
        void builder_setsAllFieldsCorrectly() {
            Category category = Category.builder()
                    .name("Algebra")
                    .slug("algebra")
                    .build();

            Quest quest = Quest.builder()
                    .title("Math Basics")
                    .build();

            List<String> hints = List.of("First hint", "Second hint");
            List<AnswerOption> answerOptions = new ArrayList<>();

            Problem problem = Problem.builder()
                    .questionText("What is 2+2?")
                    .questionImagePath("/images/question.png")
                    .problemType(ProblemType.NUMERIC)
                    .difficultyLevel(DifficultyLevel.LEVEL_1_BEGINNER)
                    .correctAnswer("4")
                    .answerTolerance(0.1)
                    .explanation("Basic addition")
                    .explanationImagePath("/images/explanation.png")
                    .orderIndex(5)
                    .xpReward(20)
                    .positionX(100)
                    .positionY(200)
                    .startNode(true)
                    .nodeIcon("math-icon")
                    .hints(hints)
                    .embedType(EmbedType.DESMOS)
                    .embedRef("desmos123")
                    .category(category)
                    .quest(quest)
                    .answerOptions(answerOptions)
                    .attempts(new ArrayList<>())
                    .build();

            assertThat(problem.getQuestionText()).isEqualTo("What is 2+2?");
            assertThat(problem.getQuestionImagePath()).isEqualTo("/images/question.png");
            assertThat(problem.getProblemType()).isEqualTo(ProblemType.NUMERIC);
            assertThat(problem.getDifficultyLevel()).isEqualTo(DifficultyLevel.LEVEL_1_BEGINNER);
            assertThat(problem.getCorrectAnswer()).isEqualTo("4");
            assertThat(problem.getAnswerTolerance()).isEqualTo(0.1);
            assertThat(problem.getExplanation()).isEqualTo("Basic addition");
            assertThat(problem.getExplanationImagePath()).isEqualTo("/images/explanation.png");
            assertThat(problem.getOrderIndex()).isEqualTo(5);
            assertThat(problem.getXpReward()).isEqualTo(20);
            assertThat(problem.getPositionX()).isEqualTo(100);
            assertThat(problem.getPositionY()).isEqualTo(200);
            assertThat(problem.getStartNode()).isTrue();
            assertThat(problem.getNodeIcon()).isEqualTo("math-icon");
            assertThat(problem.getHints()).isEqualTo(hints);
            assertThat(problem.getEmbedType()).isEqualTo(EmbedType.DESMOS);
            assertThat(problem.getEmbedRef()).isEqualTo("desmos123");
            assertThat(problem.getCategory()).isEqualTo(category);
            assertThat(problem.getQuest()).isEqualTo(quest);
            assertThat(problem.getAnswerOptions()).isEqualTo(answerOptions);
        }

        @Test
        void builder_withMinimalFields_createsValidProblem() {
            Quest quest = Quest.builder()
                    .title("Test Quest")
                    .build();

            Problem problem = Problem.builder()
                    .questionText("Test question")
                    .problemType(ProblemType.OPEN_ANSWER)
                    .difficultyLevel(DifficultyLevel.LEVEL_3_INTERMEDIATE)
                    .correctAnswer("answer")
                    .quest(quest)
                    .build();

            assertThat(problem.getQuestionText()).isEqualTo("Test question");
            assertThat(problem.getProblemType()).isEqualTo(ProblemType.OPEN_ANSWER);
            assertThat(problem.getDifficultyLevel()).isEqualTo(DifficultyLevel.LEVEL_3_INTERMEDIATE);
            assertThat(problem.getCorrectAnswer()).isEqualTo("answer");
            assertThat(problem.getQuest()).isEqualTo(quest);
            assertThat(problem.getOrderIndex()).isEqualTo(0);
            assertThat(problem.getXpReward()).isEqualTo(10);
        }
    }

    // ==================== DIFFICULTY LEVELS ====================

    @Nested
    class DifficultyLevels {

        @ParameterizedTest
        @EnumSource(DifficultyLevel.class)
        void problem_acceptsAllDifficultyLevels(DifficultyLevel level) {
            Problem problem = Problem.builder()
                    .difficultyLevel(level)
                    .build();
            assertThat(problem.getDifficultyLevel()).isEqualTo(level);
        }

        @Test
        void problem_withBeginnerDifficulty_isCorrectlySet() {
            Problem problem = Problem.builder()
                    .difficultyLevel(DifficultyLevel.LEVEL_1_BEGINNER)
                    .build();
            assertThat(problem.getDifficultyLevel()).isEqualTo(DifficultyLevel.LEVEL_1_BEGINNER);
            assertThat(problem.getDifficultyLevel().getLevelNumber()).isEqualTo(1);
            assertThat(problem.getDifficultyLevel().getDisplayName()).isEqualTo("Beginner");
        }

        @Test
        void problem_withIntermediateDifficulty_isCorrectlySet() {
            Problem problem = Problem.builder()
                    .difficultyLevel(DifficultyLevel.LEVEL_3_INTERMEDIATE)
                    .build();
            assertThat(problem.getDifficultyLevel()).isEqualTo(DifficultyLevel.LEVEL_3_INTERMEDIATE);
            assertThat(problem.getDifficultyLevel().getLevelNumber()).isEqualTo(3);
            assertThat(problem.getDifficultyLevel().getDisplayName()).isEqualTo("Intermediate");
        }

        @Test
        void problem_withMasterDifficulty_isCorrectlySet() {
            Problem problem = Problem.builder()
                    .difficultyLevel(DifficultyLevel.LEVEL_7_MASTER)
                    .build();
            assertThat(problem.getDifficultyLevel()).isEqualTo(DifficultyLevel.LEVEL_7_MASTER);
            assertThat(problem.getDifficultyLevel().getLevelNumber()).isEqualTo(7);
            assertThat(problem.getDifficultyLevel().getDisplayName()).isEqualTo("Master");
        }
    }

    // ==================== PROBLEM TYPES ====================

    @Nested
    class ProblemTypes {

        @ParameterizedTest
        @EnumSource(ProblemType.class)
        void problem_acceptsAllProblemTypes(ProblemType type) {
            Problem problem = Problem.builder()
                    .problemType(type)
                    .build();
            assertThat(problem.getProblemType()).isEqualTo(type);
        }
    }

    // ==================== RELATIONSHIPS ====================

    @Nested
    class Relationships {

        @Test
        void problem_belongsToCategory() {
            Category category = Category.builder()
                    .name("Algebra")
                    .slug("algebra")
                    .build();

            Problem problem = Problem.builder()
                    .category(category)
                    .build();

            assertThat(problem.getCategory()).isEqualTo(category);
        }

        @Test
        void problem_belongsToQuest() {
            Quest quest = Quest.builder()
                    .title("Math Basics")
                    .build();

            Problem problem = Problem.builder()
                    .quest(quest)
                    .build();

            assertThat(problem.getQuest()).isEqualTo(quest);
        }

        @Test
        void problem_canHaveAnswerOptions() {
            Problem problem = Problem.builder()
                    .answerOptions(new ArrayList<>())
                    .build();

            AnswerOption option = AnswerOption.builder()
                    .optionText("Option A")
                    .isCorrect(true)
                    .problem(problem)
                    .build();

            problem.getAnswerOptions().add(option);

            assertThat(problem.getAnswerOptions()).hasSize(1);
            assertThat(problem.getAnswerOptions().get(0)).isEqualTo(option);
        }

        @Test
        void problem_canHaveMultipleAnswerOptions() {
            Problem problem = Problem.builder()
                    .answerOptions(new ArrayList<>())
                    .build();

            AnswerOption option1 = AnswerOption.builder()
                    .optionText("Option A")
                    .isCorrect(true)
                    .problem(problem)
                    .build();

            AnswerOption option2 = AnswerOption.builder()
                    .optionText("Option B")
                    .isCorrect(false)
                    .problem(problem)
                    .build();

            problem.getAnswerOptions().add(option1);
            problem.getAnswerOptions().add(option2);

            assertThat(problem.getAnswerOptions()).hasSize(2);
        }

        @Test
        void problem_canHaveAttempts() {
            Problem problem = Problem.builder()
                    .attempts(new ArrayList<>())
                    .build();

            User user = User.builder()
                    .firstName("Test")
                    .lastName("User")
                    .email("test@example.com")
                    .passwordHash("hash")
                    .role(com.mathweb.enums.Role.STUDENT)
                    .build();

            ProblemAttempt attempt = ProblemAttempt.builder()
                    .problem(problem)
                    .user(user)
                    .build();

            problem.getAttempts().add(attempt);

            assertThat(problem.getAttempts()).hasSize(1);
        }
    }

    // ==================== HINTS ====================

    @Nested
    class Hints {

        @Test
        void hints_listIsInitializedByDefault() {
            Problem problem = Problem.builder().build();
            assertThat(problem.getHints()).isNotNull();
            assertThat(problem.getHints()).isEmpty();
        }

        @Test
        void hints_canBeSet() {
            List<String> hints = List.of("First hint", "Second hint", "Third hint");
            Problem problem = Problem.builder()
                    .hints(hints)
                    .build();

            assertThat(problem.getHints()).isEqualTo(hints);
        }

        @Test
        void hints_canBeModifiedAfterCreation() {
            Problem problem = Problem.builder()
                    .hints(new ArrayList<>(List.of("Initial hint")))
                    .build();

            problem.getHints().add("Additional hint");

            assertThat(problem.getHints()).containsExactly("Initial hint", "Additional hint");
        }

        @Test
        void hints_canBeEmptyList() {
            Problem problem = Problem.builder()
                    .hints(new ArrayList<>())
                    .build();

            assertThat(problem.getHints()).isEmpty();
        }
    }

    // ==================== EMBED ====================

    @Nested
    class Embed {

        @ParameterizedTest
        @EnumSource(EmbedType.class)
        void embedType_acceptsAllValues(EmbedType type) {
            Problem problem = Problem.builder()
                    .embedType(type)
                    .embedRef("ref123")
                    .build();
            assertThat(problem.getEmbedType()).isEqualTo(type);
            assertThat(problem.getEmbedRef()).isEqualTo("ref123");
        }

        @Test
        void embedType_canBeNull() {
            Problem problem = Problem.builder()
                    .embedType(null)
                    .build();
            assertThat(problem.getEmbedType()).isNull();
        }

        @Test
        void embedRef_canBeNull() {
            Problem problem = Problem.builder()
                    .embedRef(null)
                    .build();
            assertThat(problem.getEmbedRef()).isNull();
        }
    }

    // ==================== POSITION ====================

    @Nested
    class Position {

        @Test
        void positionX_canBeNull() {
            Problem problem = Problem.builder()
                    .positionX(null)
                    .build();
            assertThat(problem.getPositionX()).isNull();
        }

        @Test
        void positionY_canBeNull() {
            Problem problem = Problem.builder()
                    .positionY(null)
                    .build();
            assertThat(problem.getPositionY()).isNull();
        }

        @Test
        void positionX_and_positionY_canBeSet() {
            Problem problem = Problem.builder()
                    .positionX(100)
                    .positionY(200)
                    .build();

            assertThat(problem.getPositionX()).isEqualTo(100);
            assertThat(problem.getPositionY()).isEqualTo(200);
        }
    }
}
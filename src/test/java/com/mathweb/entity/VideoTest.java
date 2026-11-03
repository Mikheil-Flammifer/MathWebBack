package com.mathweb.entity;

import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.VideoStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Video} entity.
 * Tests field defaults, business logic, and relationships WITHOUT using .id()
 */
class VideoTest {

    // ==================== DEFAULT VALUES ====================

    @Nested
    class DefaultValues {

        @Test
        void newlyCreatedVideo_hasDefaultViewCountZero() {
            Video video = Video.builder().build();
            assertThat(video.getViewCount()).isEqualTo(0L);
        }

        @Test
        void newlyCreatedVideo_hasNullDuration() {
            Video video = Video.builder().build();
            assertThat(video.getDurationSeconds()).isNull();
        }

        @Test
        void newlyCreatedVideo_hasDefaultFreeFalse() {
            Video video = Video.builder().build();
            assertThat(video.isFree()).isFalse();
        }

        @Test
        void newlyCreatedVideo_hasDefaultStatusUploading() {
            Video video = Video.builder().build();
            assertThat(video.getStatus()).isEqualTo(VideoStatus.UPLOADING);
        }

        @Test
        void newlyCreatedVideo_hasNullOptionalFields() {
            Video video = Video.builder().build();
            assertThat(video.getTitle()).isNull();
            assertThat(video.getDescription()).isNull();
            assertThat(video.getFilePath()).isNull();
            assertThat(video.getThumbnailPath()).isNull();
            assertThat(video.getFileSizeBytes()).isNull();
            assertThat(video.getMimeType()).isNull();
            assertThat(video.getDifficultyLevel()).isNull();
            assertThat(video.getUploadedBy()).isNull();
            assertThat(video.getQuest()).isNull();
            assertThat(video.getCategory()).isNull();
        }

        @Test
        void newlyCreatedVideo_hasEmptyCommentsList() {
            Video video = Video.builder().build();
            assertThat(video.getComments()).isNotNull();
            assertThat(video.getComments()).isEmpty();
        }
    }

    // ==================== BUILDER ====================

    @Nested
    class Builder {

        @Test
        void builder_setsAllFieldsCorrectly() {
            User uploadedBy = User.builder()
                    .firstName("Teacher")
                    .lastName("Math")
                    .email("teacher@math.com")
                    .passwordHash("hash")
                    .role(com.mathweb.enums.Role.TEACHER)
                    .build();

            Quest quest = Quest.builder()
                    .title("Math Basics")
                    .build();

            Category category = Category.builder()
                    .name("Algebra")
                    .slug("algebra")
                    .build();

            Video video = Video.builder()
                    .title("Introduction to Algebra")
                    .description("Basic algebra concepts")
                    .filePath("/videos/algebra-intro.mp4")
                    .thumbnailPath("/thumbnails/algebra-intro.jpg")
                    .durationSeconds(3600L)
                    .fileSizeBytes(52_428_800L)
                    .mimeType("video/mp4")
                    .free(true)
                    .status(VideoStatus.PUBLISHED)
                    .difficultyLevel(DifficultyLevel.LEVEL_2_ELEMENTARY)
                    .viewCount(100L)
                    .uploadedBy(uploadedBy)
                    .quest(quest)
                    .category(category)
                    .comments(new ArrayList<>())
                    .build();

            assertThat(video.getTitle()).isEqualTo("Introduction to Algebra");
            assertThat(video.getDescription()).isEqualTo("Basic algebra concepts");
            assertThat(video.getFilePath()).isEqualTo("/videos/algebra-intro.mp4");
            assertThat(video.getThumbnailPath()).isEqualTo("/thumbnails/algebra-intro.jpg");
            assertThat(video.getDurationSeconds()).isEqualTo(3600L);
            assertThat(video.getFileSizeBytes()).isEqualTo(52_428_800L);
            assertThat(video.getMimeType()).isEqualTo("video/mp4");
            assertThat(video.isFree()).isTrue();
            assertThat(video.getStatus()).isEqualTo(VideoStatus.PUBLISHED);
            assertThat(video.getDifficultyLevel()).isEqualTo(DifficultyLevel.LEVEL_2_ELEMENTARY);
            assertThat(video.getViewCount()).isEqualTo(100L);
            assertThat(video.getUploadedBy()).isEqualTo(uploadedBy);
            assertThat(video.getQuest()).isEqualTo(quest);
            assertThat(video.getCategory()).isEqualTo(category);
            assertThat(video.getComments()).isEmpty();
        }

        @Test
        void builder_withMinimalFields_createsValidVideo() {
            Video video = Video.builder()
                    .title("Test Video")
                    .filePath("/videos/test.mp4")
                    .build();

            assertThat(video.getTitle()).isEqualTo("Test Video");
            assertThat(video.getFilePath()).isEqualTo("/videos/test.mp4");
            assertThat(video.getStatus()).isEqualTo(VideoStatus.UPLOADING);
            assertThat(video.getViewCount()).isEqualTo(0L);
            assertThat(video.getDurationSeconds()).isNull();
            assertThat(video.isFree()).isFalse();
        }
    }

    // ==================== VIDEO STATUS ====================

    @Nested
    class Status {

        @ParameterizedTest
        @EnumSource(VideoStatus.class)
        void video_acceptsAllStatusValues(VideoStatus status) {
            Video video = Video.builder()
                    .status(status)
                    .build();
            assertThat(video.getStatus()).isEqualTo(status);
        }

        @Test
        void video_withUploadingStatus_isCorrectlySet() {
            Video video = Video.builder()
                    .status(VideoStatus.UPLOADING)
                    .build();
            assertThat(video.getStatus()).isEqualTo(VideoStatus.UPLOADING);
        }

        @Test
        void video_withProcessingStatus_isCorrectlySet() {
            Video video = Video.builder()
                    .status(VideoStatus.PROCESSING)
                    .build();
            assertThat(video.getStatus()).isEqualTo(VideoStatus.PROCESSING);
        }

        @Test
        void video_withPublishedStatus_isCorrectlySet() {
            Video video = Video.builder()
                    .status(VideoStatus.PUBLISHED)
                    .build();
            assertThat(video.getStatus()).isEqualTo(VideoStatus.PUBLISHED);
        }

        @Test
        void video_withDraftStatus_isCorrectlySet() {
            Video video = Video.builder()
                    .status(VideoStatus.DRAFT)
                    .build();
            assertThat(video.getStatus()).isEqualTo(VideoStatus.DRAFT);
        }

        @Test
        void video_withArchivedStatus_isCorrectlySet() {
            Video video = Video.builder()
                    .status(VideoStatus.ARCHIVED)
                    .build();
            assertThat(video.getStatus()).isEqualTo(VideoStatus.ARCHIVED);
        }

        @Test
        void status_canBeChangedAfterCreation() {
            Video video = Video.builder().build();
            video.setStatus(VideoStatus.PUBLISHED);
            assertThat(video.getStatus()).isEqualTo(VideoStatus.PUBLISHED);
        }
    }

    // ==================== DIFFICULTY LEVEL ====================

    @Nested
    class Difficulty {

        @ParameterizedTest
        @EnumSource(DifficultyLevel.class)
        void video_acceptsAllDifficultyLevels(DifficultyLevel level) {
            Video video = Video.builder()
                    .difficultyLevel(level)
                    .build();
            assertThat(video.getDifficultyLevel()).isEqualTo(level);
        }

        @Test
        void difficultyLevel_canBeNull() {
            Video video = Video.builder()
                    .difficultyLevel(null)
                    .build();
            assertThat(video.getDifficultyLevel()).isNull();
        }
    }

    // ==================== VIEW COUNT ====================

    @Nested
    class ViewCount {

        @Test
        void viewCount_startsAtZero() {
            Video video = Video.builder().build();
            assertThat(video.getViewCount()).isZero();
        }

        @Test
        void viewCount_canBeSetToPositiveValue() {
            Video video = Video.builder()
                    .viewCount(50L)
                    .build();
            assertThat(video.getViewCount()).isEqualTo(50L);
        }

        @Test
        void viewCount_canBeSetToZero() {
            Video video = Video.builder()
                    .viewCount(0L)
                    .build();
            assertThat(video.getViewCount()).isZero();
        }

        @Test
        void viewCount_canBeSetToLargeValue() {
            Video video = Video.builder()
                    .viewCount(1_000_000L)
                    .build();
            assertThat(video.getViewCount()).isEqualTo(1_000_000L);
        }

        @Test
        void viewCount_canBeIncrementedAfterCreation() {
            Video video = Video.builder().build();
            video.setViewCount(video.getViewCount() + 1);
            assertThat(video.getViewCount()).isEqualTo(1L);
        }
    }

    // ==================== IS FREE ====================

    @Nested
    class Free {

        @Test
        void free_defaultsToFalse() {
            Video video = Video.builder().build();
            assertThat(video.isFree()).isFalse();
        }

        @Test
        void free_canBeSetToTrue() {
            Video video = Video.builder()
                    .free(true)
                    .build();
            assertThat(video.isFree()).isTrue();
        }

        @Test
        void free_canBeSetToFalse() {
            Video video = Video.builder()
                    .free(false)
                    .build();
            assertThat(video.isFree()).isFalse();
        }

        @Test
        void free_canBeChangedWithSetter() {
            Video video = Video.builder().build();
            video.setFree(true);
            assertThat(video.isFree()).isTrue();
        }
    }

    // ==================== DURATION ====================

    @Nested
    class Duration {

        @Test
        void durationSeconds_defaultsToNull() {
            Video video = Video.builder().build();
            assertThat(video.getDurationSeconds()).isNull();
        }

        @Test
        void durationSeconds_canBeSetToPositiveValue() {
            Video video = Video.builder()
                    .durationSeconds(1800L) // 30 minutes
                    .build();
            assertThat(video.getDurationSeconds()).isEqualTo(1800L);
        }

        @Test
        void durationSeconds_canBeSetToZero() {
            Video video = Video.builder()
                    .durationSeconds(0L)
                    .build();
            assertThat(video.getDurationSeconds()).isZero();
        }
    }

    // ==================== FILE METADATA ====================

    @Nested
    class FileMetadata {

        @Test
        void fileSizeBytes_canBeSet() {
            Video video = Video.builder()
                    .fileSizeBytes(1024L)
                    .build();
            assertThat(video.getFileSizeBytes()).isEqualTo(1024L);
        }

        @Test
        void mimeType_canBeSet() {
            Video video = Video.builder()
                    .mimeType("video/mp4")
                    .build();
            assertThat(video.getMimeType()).isEqualTo("video/mp4");
        }
    }

    // ==================== RELATIONSHIPS ====================

    @Nested
    class Relationships {

        @Test
        void video_hasUploadedByUser() {
            User user = User.builder()
                    .firstName("Teacher")
                    .lastName("Math")
                    .email("teacher@math.com")
                    .passwordHash("hash")
                    .role(com.mathweb.enums.Role.TEACHER)
                    .build();

            Video video = Video.builder()
                    .uploadedBy(user)
                    .build();

            assertThat(video.getUploadedBy()).isEqualTo(user);
        }

        @Test
        void video_canBelongToQuest() {
            Quest quest = Quest.builder()
                    .title("Math Basics")
                    .build();

            Video video = Video.builder()
                    .quest(quest)
                    .build();

            assertThat(video.getQuest()).isEqualTo(quest);
        }

        @Test
        void video_canBelongToCategory() {
            Category category = Category.builder()
                    .name("Algebra")
                    .slug("algebra")
                    .build();

            Video video = Video.builder()
                    .category(category)
                    .build();

            assertThat(video.getCategory()).isEqualTo(category);
        }

        @Test
        void video_canHaveComments() {
            Video video = Video.builder()
                    .comments(new ArrayList<>())
                    .build();

            User user = User.builder()
                    .firstName("Student")
                    .lastName("Test")
                    .email("student@test.com")
                    .passwordHash("hash")
                    .role(com.mathweb.enums.Role.STUDENT)
                    .build();

            Comment comment = Comment.builder()
                    .content("Great video!")
                    .video(video)
                    .user(user)
                    .build();

            video.getComments().add(comment);

            assertThat(video.getComments()).hasSize(1);
            assertThat(video.getComments().get(0)).isEqualTo(comment);
        }

        @Test
        void video_canHaveMultipleComments() {
            Video video = Video.builder()
                    .comments(new ArrayList<>())
                    .build();

            User user1 = User.builder()
                    .firstName("User1")
                    .lastName("Test")
                    .email("user1@test.com")
                    .passwordHash("hash")
                    .role(com.mathweb.enums.Role.STUDENT)
                    .build();

            User user2 = User.builder()
                    .firstName("User2")
                    .lastName("Test")
                    .email("user2@test.com")
                    .passwordHash("hash")
                    .role(com.mathweb.enums.Role.STUDENT)
                    .build();

            Comment comment1 = Comment.builder()
                    .content("First comment")
                    .video(video)
                    .user(user1)
                    .build();

            Comment comment2 = Comment.builder()
                    .content("Second comment")
                    .video(video)
                    .user(user2)
                    .build();

            video.getComments().add(comment1);
            video.getComments().add(comment2);

            assertThat(video.getComments()).hasSize(2);
        }
    }

    // ==================== FIELD VALIDATION ====================

    @Nested
    class FieldValidation {

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t", "\n"})
        void title_canBeEmptyOrWhitespace(String title) {
            Video video = Video.builder()
                    .title(title)
                    .build();
            assertThat(video.getTitle()).isEqualTo(title);
        }

        @Test
        void title_canBeNull() {
            Video video = Video.builder()
                    .title(null)
                    .build();
            assertThat(video.getTitle()).isNull();
        }

        @Test
        void description_canBeNull() {
            Video video = Video.builder()
                    .description(null)
                    .build();
            assertThat(video.getDescription()).isNull();
        }

        @Test
        void description_canBeEmpty() {
            Video video = Video.builder()
                    .description("")
                    .build();
            assertThat(video.getDescription()).isEmpty();
        }

        @Test
        void filePath_canBeSet() {
            Video video = Video.builder()
                    .filePath("/videos/test.mp4")
                    .build();
            assertThat(video.getFilePath()).isEqualTo("/videos/test.mp4");
        }

        @Test
        void thumbnailPath_canBeNull() {
            Video video = Video.builder()
                    .thumbnailPath(null)
                    .build();
            assertThat(video.getThumbnailPath()).isNull();
        }

        @Test
        void status_canBeExplicitlySetToNull() {
            Video video = Video.builder()
                    .status(null)
                    .build();
            assertThat(video.getStatus()).isNull();
        }
    }
}
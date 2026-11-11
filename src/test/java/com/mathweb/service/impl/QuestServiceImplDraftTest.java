package com.mathweb.service.impl;

import com.mathweb.entity.Quest;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.entity.AnswerOption;
import com.mathweb.entity.Problem;
import com.mathweb.service.FileStorageService;
import com.mathweb.repository.ProblemRepository;
import com.mathweb.repository.QuestRepository;
import com.mathweb.repository.UserQuestProgressRepository;
import com.mathweb.repository.VideoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class QuestServiceImplDraftTest {

    @Mock private QuestMapService questMapService;
    @Mock private QuestRepository questRepository;
    @Mock private UserQuestProgressRepository progressRepository;
    @Mock private ProblemRepository problemRepository;
    @Mock private VideoRepository videoRepository;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks private QuestServiceImpl service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("u", "p", "ROLE_" + role));
    }

    private Quest quest(boolean published) {
        Quest q = Quest.builder().title("Algebra basics").published(published).build();
        q.setId(5L);
        when(questRepository.findById(5L)).thenReturn(Optional.of(q));
        return q;
    }

    @Test
    void student_cannotSeeDraftQuest() {
        loginAs("STUDENT");
        quest(false);

        assertThatThrownBy(() -> service.getQuestById(5L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void student_canSeePublishedQuest() {
        loginAs("STUDENT");
        quest(true);

        assertThat(service.getQuestById(5L, 1L).getId()).isEqualTo(5L);
    }

    @Test
    void teacher_canSeeDraftQuest() {
        loginAs("TEACHER");
        quest(false);

        assertThat(service.getQuestById(5L, 1L).getPublished()).isFalse();
    }
 
    @Test
    void deleteQuest_keepsVideos_andRemovesProblemImages() {
        Quest q = quest(true);
        Problem p = Problem.builder()
                .questionImagePath("/up/images/q.png")
                .explanationImagePath("/up/images/e.png")
                .build();
        p.getAnswerOptions().add(AnswerOption.builder()
                .optionText("a").isCorrect(true).optionImagePath("/up/images/o.png").build());
        q.getProblems().add(p);

        service.deleteQuest(5L);

        InOrder order = inOrder(videoRepository, questRepository);
        order.verify(videoRepository).detachFromQuest(5L);
        order.verify(questRepository).delete(q);
        verify(fileStorageService).deleteFile("/up/images/q.png");
        verify(fileStorageService).deleteFile("/up/images/e.png");
        verify(fileStorageService).deleteFile("/up/images/o.png");
    }
}
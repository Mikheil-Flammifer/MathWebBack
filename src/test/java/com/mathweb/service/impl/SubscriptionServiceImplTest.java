package com.mathweb.service.impl;

import com.mathweb.dto.response.SubscriptionResponse;
import com.mathweb.entity.Subscription;
import com.mathweb.entity.User;
import com.mathweb.enums.SubscriptionStatus;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.mapper.SubscriptionMapper;
import com.mathweb.repository.SubscriptionRepository;
import com.mathweb.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Unit tests for {@link SubscriptionServiceImpl}. */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubscriptionMapper subscriptionMapper;

    @InjectMocks private SubscriptionServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().email("student@example.com").build();
        user.setId(1L);
    }

    @Test
    void getMySubscription_returnsTheMappedSubscription_whenOneExists() {
        Subscription subscription = Subscription.builder()
                .user(user)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodEnd(LocalDateTime.now().plusDays(30))
                .build();
        SubscriptionResponse mapped = SubscriptionResponse.builder().isActive(true).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(subscription));
        when(subscriptionMapper.toResponse(subscription)).thenReturn(mapped);

        SubscriptionResponse result = service.getMySubscription(1L);

        assertThat(result).isSameAs(mapped);
    }

    @Test
    void getMySubscription_returnsAnInactiveResponse_whenUserHasNoSubscription() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.empty());

        SubscriptionResponse result = service.getMySubscription(1L);

        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(SubscriptionResponse.builder().isActive(false).build());
        verifyNoInteractions(subscriptionMapper);
    }

    @Test
    void getMySubscription_throwsNotFound_whenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMySubscription(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(subscriptionRepository, subscriptionMapper);
    }
}
package com.mathweb.service.impl;

import com.mathweb.dto.response.SubscriptionResponse;
import com.mathweb.entity.User;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.mapper.SubscriptionMapper;
import com.mathweb.repository.SubscriptionRepository;
import com.mathweb.repository.UserRepository;
import com.mathweb.service.SubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final SubscriptionMapper subscriptionMapper;

    public SubscriptionServiceImpl(SubscriptionRepository subscriptionRepository,
                                   UserRepository userRepository,
                                   SubscriptionMapper subscriptionMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.subscriptionMapper = subscriptionMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse getMySubscription(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        return subscriptionRepository.findByUser(user)
                .map(subscriptionMapper::toResponse)
                .orElseGet(() -> SubscriptionResponse.builder()
                        .isActive(false)
                        .build());
    }
}
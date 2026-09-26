package com.mathweb.mapper;

import com.mathweb.dto.response.SubscriptionResponse;
import com.mathweb.entity.Subscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    @Mapping(target = "isActive",
            expression = "java(subscription.isCurrentlyActive())")
    SubscriptionResponse toResponse(Subscription subscription);
}
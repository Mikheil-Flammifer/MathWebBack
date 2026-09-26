package com.mathweb.mapper;

import com.mathweb.dto.response.UserResponse;
import com.mathweb.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "hasActiveSubscription",
            expression = "java(user.hasActiveSubscription())")
    UserResponse toResponse(User user);
}
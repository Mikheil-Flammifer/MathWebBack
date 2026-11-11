package com.mathweb.mapper;

import com.mathweb.dto.response.UserResponse;
import com.mathweb.entity.User;
import com.mathweb.service.FileStorageService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class UserMapper {

    @Autowired
    protected FileStorageService fileStorageService;

    @Mapping(target = "hasActiveSubscription",
            expression = "java(user.hasActiveSubscription())")
    @Mapping(target = "avatarUrl",
            expression = "java(fileStorageService.getFileUrl(user.getAvatarUrl()))")
    public abstract UserResponse toResponse(User user);
}
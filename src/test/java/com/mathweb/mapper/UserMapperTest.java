package com.mathweb.mapper;

import com.mathweb.dto.response.UserResponse;
import com.mathweb.entity.User;
import com.mathweb.service.impl.FileStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private UserMapperImpl mapper;

    @BeforeEach
    void setUp() {
        FileStorageServiceImpl storage = new FileStorageServiceImpl();
        ReflectionTestUtils.setField(storage, "baseUrl", "https://api.example.com");
        mapper = new UserMapperImpl();
        ReflectionTestUtils.setField(mapper, "fileStorageService", storage);
    }

    @Test
    void avatarPath_isReturnedAsPublicUrl_withoutServerPath() {
        User user = User.builder()
                .avatarUrl("/var/data/uploads/images/abc.png")
                .build();

        UserResponse response = mapper.toResponse(user);

        assertThat(response.getAvatarUrl())
                .isEqualTo("https://api.example.com/uploads/images/abc.png");
        assertThat(response.getAvatarUrl()).doesNotContain("/var/data");
    }

    @Test
    void noAvatar_staysNull() {
        assertThat(mapper.toResponse(User.builder().build()).getAvatarUrl()).isNull();
    }
}
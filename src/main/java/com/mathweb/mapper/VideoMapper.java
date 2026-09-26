package com.mathweb.mapper;

import com.mathweb.dto.response.VideoResponse;
import com.mathweb.entity.Video;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface VideoMapper {

    @Mapping(target = "questId",
            expression = "java(video.getQuest() != null ? video.getQuest().getId() : null)")
    @Mapping(target = "questTitle",
            expression = "java(video.getQuest() != null ? video.getQuest().getTitle() : null)")
    @Mapping(target = "uploadedBy",
            source = "uploadedBy")
    @Mapping(target = "commentCount", constant = "0L")
    VideoResponse toResponse(Video video);
}
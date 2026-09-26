package com.mathweb.mapper;

import com.mathweb.dto.response.CommentResponse;
import com.mathweb.entity.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface CommentMapper {

    @Mapping(target = "parentId",
            expression = "java(comment.getParent() != null ? comment.getParent().getId() : null)")
    @Mapping(target = "videoId",
            source = "video.id")
    @Mapping(target = "content",
            expression = "java(comment.getDisplayContent())")
    @Mapping(target = "replies",
            ignore = true)
    CommentResponse toResponse(Comment comment);
}
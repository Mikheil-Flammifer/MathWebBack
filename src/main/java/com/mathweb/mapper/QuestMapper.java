package com.mathweb.mapper;

import com.mathweb.dto.response.QuestResponse;
import com.mathweb.entity.Quest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface QuestMapper {

    @Mapping(target = "prerequisiteIds",
            expression = "java(quest.getPrerequisites().stream().map(q -> q.getId()).collect(java.util.stream.Collectors.toList()))")
    @Mapping(target = "dependentIds",
            expression = "java(quest.getDependents().stream().map(q -> q.getId()).collect(java.util.stream.Collectors.toList()))")
    @Mapping(target = "totalProblems", constant = "0")
    @Mapping(target = "totalVideos", constant = "0")
    @Mapping(target = "userStatus", ignore = true)
    @Mapping(target = "userProblemsSolved", ignore = true)
    @Mapping(target = "userProgressPercentage", ignore = true)
    QuestResponse toResponse(Quest quest);
}
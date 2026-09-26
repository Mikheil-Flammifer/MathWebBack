package com.mathweb.mapper;

import com.mathweb.dto.response.AnswerOptionResponse;
import com.mathweb.dto.response.ProblemResponse;
import com.mathweb.entity.AnswerOption;
import com.mathweb.entity.Problem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProblemMapper {

    @Mapping(target = "questId", source = "quest.id")
    @Mapping(target = "answerOptions", ignore = true)
    @Mapping(target = "attemptsUsed", constant = "0")
    @Mapping(target = "solved", constant = "false")
    @Mapping(target = "answerRevealed", constant = "false")
    @Mapping(target = "explanation", ignore = true)
    @Mapping(target = "explanationImagePath", ignore = true)
    @Mapping(target = "correctAnswer", ignore = true)
    ProblemResponse toResponse(Problem problem);

    @Mapping(target = "optionImagePath", source = "optionImagePath")
    AnswerOptionResponse toAnswerOptionResponse(AnswerOption answerOption);
}
package com.mathweb.service;

import com.mathweb.dto.request.CreateProblemRequest;
import com.mathweb.dto.request.SubmitAnswerRequest;
import com.mathweb.dto.response.AnswerResultResponse;
import com.mathweb.dto.response.ProblemResponse;

import java.util.List;

public interface ProblemService {
    ProblemResponse createProblem(CreateProblemRequest request);
    ProblemResponse getProblemById(Long id, Long userId);
    List<ProblemResponse> getProblemsByQuest(Long questId, Long userId);
    AnswerResultResponse submitAnswer(SubmitAnswerRequest request, Long userId);
    void deleteProblem(Long id);
}
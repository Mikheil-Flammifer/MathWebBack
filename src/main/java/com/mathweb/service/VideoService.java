package com.mathweb.service;

import com.mathweb.dto.request.UpdateVideoRequest;
import com.mathweb.dto.request.VideoUploadRequest;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.dto.response.VideoResponse;
import com.mathweb.enums.DifficultyLevel;
import org.springframework.web.multipart.MultipartFile;

public interface VideoService {
    VideoResponse uploadVideo(MultipartFile file, VideoUploadRequest request, Long uploaderId);
    VideoResponse getVideoById(Long id);
    PageResponse<VideoResponse> getAllVideos(int page, int size, DifficultyLevel level);
    PageResponse<VideoResponse> searchVideos(String keyword, int page, int size);
    VideoResponse updateVideo(Long id, UpdateVideoRequest request, Long userId);
    void deleteVideo(Long id, Long userId);
    void incrementViewCount(Long id);
}
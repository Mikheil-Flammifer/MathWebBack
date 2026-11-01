package com.mathweb.service;

import com.mathweb.dto.request.UpdateVideoRequest;
import com.mathweb.dto.request.VideoUploadRequest;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.dto.response.VideoResponse;
import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.VideoStatus;
import org.springframework.web.multipart.MultipartFile;

public interface VideoService {
    VideoResponse getVideoById(Long id, Long userId);
    VideoStream openStream(Long id, Long userId);
    VideoResponse uploadVideo(MultipartFile file, VideoUploadRequest request, Long uploaderId);
    VideoResponse getVideoById(Long id);
    PageResponse<VideoResponse> getAllVideos(int page, int size, DifficultyLevel level);
    PageResponse<VideoResponse> searchVideos(String keyword, int page, int size);
    VideoResponse updateVideo(Long id, UpdateVideoRequest request, Long userId);
    VideoResponse uploadThumbnail(Long videoId, MultipartFile thumbnail, Long userId);
    PageResponse<VideoResponse> getAllVideos(int page, int size, DifficultyLevel level, Long categoryId, String sort);
    void deleteVideo(Long id, Long userId);
    void incrementViewCount(Long id);

    /** Staff listing across every status. status == null means all; mine limits to the caller's uploads. */
    PageResponse<VideoResponse> getVideosForStaff(int page, int size, VideoStatus status,
                                                  String keyword, boolean mine, Long userId);
}
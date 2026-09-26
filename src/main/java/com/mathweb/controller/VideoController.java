package com.mathweb.controller;

import com.mathweb.dto.request.UpdateVideoRequest;
import com.mathweb.dto.request.VideoUploadRequest;
import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.dto.response.VideoResponse;
import com.mathweb.enums.DifficultyLevel;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.VideoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VideoResponse>>> getAllVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) DifficultyLevel level) {
        PageResponse<VideoResponse> videos = videoService.getAllVideos(page, size, level);
        return ResponseEntity.ok(ApiResponse.success("Videos retrieved", videos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VideoResponse>> getVideoById(@PathVariable Long id) {
        videoService.incrementViewCount(id);
        VideoResponse video = videoService.getVideoById(id);
        return ResponseEntity.ok(ApiResponse.success("Video retrieved", video));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<VideoResponse>>> searchVideos(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<VideoResponse> videos = videoService.searchVideos(keyword, page, size);
        return ResponseEntity.ok(ApiResponse.success("Search results", videos));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<VideoResponse>> uploadVideo(
            @RequestPart("file") MultipartFile file,
            @RequestPart("data") @Valid VideoUploadRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        VideoResponse video = videoService.uploadVideo(file, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Video uploaded successfully", video));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VideoResponse>> updateVideo(
            @PathVariable Long id,
            @Valid @RequestBody UpdateVideoRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        VideoResponse video = videoService.updateVideo(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Video updated", video));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteVideo(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        videoService.deleteVideo(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Video deleted"));
    }
}
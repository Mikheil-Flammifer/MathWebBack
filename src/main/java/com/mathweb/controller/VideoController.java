package com.mathweb.controller;

import com.mathweb.dto.request.UpdateVideoRequest;
import com.mathweb.dto.request.VideoUploadRequest;
import com.mathweb.dto.response.ApiResponse;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.dto.response.VideoResponse;
import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.VideoStatus;
import com.mathweb.security.UserPrincipal;
import com.mathweb.service.VideoService;
import com.mathweb.service.VideoStream;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
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
            @RequestParam(required = false) DifficultyLevel level,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "newest") String sort) {
        PageResponse<VideoResponse> videos =
                videoService.getAllVideos(page, size, level, categoryId, sort);
        return ResponseEntity.ok(ApiResponse.success("Videos retrieved", videos));
    }

    /**
     * Staff listing with every status (draft, published, archived).
     * status is optional (all statuses when absent); mine=true limits to the caller's uploads.
     */
    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<ApiResponse<PageResponse<VideoResponse>>> getVideosForStaff(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) VideoStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean mine,
            @AuthenticationPrincipal UserPrincipal principal) {
        PageResponse<VideoResponse> videos = videoService.getVideosForStaff(
                page, size, status, keyword, mine, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Videos retrieved", videos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VideoResponse>> getVideoById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        VideoResponse video = videoService.getVideoById(id, principal.getId());
        videoService.incrementViewCount(id);
        return ResponseEntity.ok(ApiResponse.success("Video retrieved", video));
    }

    @GetMapping("/{id}/stream")
    public ResponseEntity<Resource> streamVideo(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        VideoStream stream = videoService.openStream(id, principal.getId());

        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (stream.mimeType() != null) {
            try {
                type = MediaType.parseMediaType(stream.mimeType());
            } catch (InvalidMediaTypeException ignored) { }
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CACHE_CONTROL, "private")
                .body(stream.resource());
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
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<ApiResponse<VideoResponse>> uploadVideo(
            @RequestPart("file") MultipartFile file,
            @RequestPart("data") @Valid VideoUploadRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        VideoResponse video = videoService.uploadVideo(file, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Video uploaded successfully", video));
    }

    @PostMapping(value = "/{id}/thumbnail",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<ApiResponse<VideoResponse>> uploadThumbnail(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile thumbnail,
            @AuthenticationPrincipal UserPrincipal principal) {
        VideoResponse video = videoService.uploadThumbnail(
                id, thumbnail, principal.getId());
        return ResponseEntity.ok(
                ApiResponse.success("Thumbnail uploaded", video));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<ApiResponse<VideoResponse>> updateVideo(
            @PathVariable Long id,
            @Valid @RequestBody UpdateVideoRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        VideoResponse video = videoService.updateVideo(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Video updated", video));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<ApiResponse<Void>> deleteVideo(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        videoService.deleteVideo(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Video deleted"));
    }
}
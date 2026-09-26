package com.mathweb.service.impl;

import com.mathweb.dto.request.UpdateVideoRequest;
import com.mathweb.dto.request.VideoUploadRequest;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.dto.response.UserResponse;
import com.mathweb.dto.response.VideoResponse;
import com.mathweb.entity.Quest;
import com.mathweb.entity.User;
import com.mathweb.entity.Video;
import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.VideoStatus;
import com.mathweb.exception.ForbiddenException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.CommentRepository;
import com.mathweb.repository.QuestRepository;
import com.mathweb.repository.UserRepository;
import com.mathweb.repository.VideoRepository;
import com.mathweb.service.FileStorageService;
import com.mathweb.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VideoServiceImpl implements VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoServiceImpl.class);

    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final QuestRepository questRepository;
    private final CommentRepository commentRepository;
    private final FileStorageService fileStorageService;

    public VideoServiceImpl(VideoRepository videoRepository,
                            UserRepository userRepository,
                            QuestRepository questRepository,
                            CommentRepository commentRepository,
                            FileStorageService fileStorageService) {
        this.videoRepository = videoRepository;
        this.userRepository = userRepository;
        this.questRepository = questRepository;
        this.commentRepository = commentRepository;
        this.fileStorageService = fileStorageService;
    }

    @Override
    @Transactional
    public VideoResponse uploadVideo(MultipartFile file,
                                     VideoUploadRequest request,
                                     Long uploaderId) {
        User uploader = userRepository.findById(uploaderId)
                .orElseThrow(() -> new ResourceNotFoundException("User", uploaderId));

        String filePath = fileStorageService.storeVideo(file);

        Video video = Video.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .filePath(filePath)
                .mimeType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .difficultyLevel(request.getDifficultyLevel())
                .status(VideoStatus.PUBLISHED)
                .uploadedBy(uploader)
                .viewCount(0L)
                .build();

        if (request.getQuestId() != null) {
            Quest quest = questRepository.findById(request.getQuestId())
                    .orElseThrow(() -> new ResourceNotFoundException("Quest", request.getQuestId()));
            video.setQuest(quest);
        }

        videoRepository.save(video);
        log.info("Video uploaded: {} by user {}", video.getTitle(), uploaderId);

        return mapToVideoResponse(video);
    }

    @Override
    @Transactional(readOnly = true)
    public VideoResponse getVideoById(Long id) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video", id));
        return mapToVideoResponse(video);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VideoResponse> getAllVideos(int page, int size, DifficultyLevel level) {
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by("createdAt").descending());

        Page<Video> videoPage;
        if (level != null) {
            videoPage = videoRepository.findByDifficultyLevelAndStatus(
                    level, VideoStatus.PUBLISHED, pageRequest);
        } else {
            videoPage = videoRepository.findByStatus(VideoStatus.PUBLISHED, pageRequest);
        }

        return mapToPageResponse(videoPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VideoResponse> searchVideos(String keyword, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by("createdAt").descending());
        Page<Video> videoPage = videoRepository.searchByTitle(keyword, pageRequest);
        return mapToPageResponse(videoPage);
    }

    @Override
    @Transactional
    public VideoResponse updateVideo(Long id, UpdateVideoRequest request, Long userId) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video", id));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // Only uploader or admin can update
        boolean isAdmin = user.getRole().name().equals("ADMIN");
        boolean isUploader = video.getUploadedBy().getId().equals(userId);

        if (!isAdmin && !isUploader) {
            throw new ForbiddenException("You are not allowed to update this video");
        }

        if (request.getTitle() != null) video.setTitle(request.getTitle());
        if (request.getDescription() != null) video.setDescription(request.getDescription());
        if (request.getDifficultyLevel() != null) video.setDifficultyLevel(request.getDifficultyLevel());
        if (request.getStatus() != null) video.setStatus(request.getStatus());
        if (request.getQuestId() != null) {
            Quest quest = questRepository.findById(request.getQuestId())
                    .orElseThrow(() -> new ResourceNotFoundException("Quest", request.getQuestId()));
            video.setQuest(quest);
        }

        videoRepository.save(video);
        return mapToVideoResponse(video);
    }

    @Override
    @Transactional
    public void deleteVideo(Long id, Long userId) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video", id));

        fileStorageService.deleteFile(video.getFilePath());
        if (video.getThumbnailPath() != null) {
            fileStorageService.deleteFile(video.getThumbnailPath());
        }

        videoRepository.delete(video);
        log.info("Video deleted: {} by user {}", id, userId);
    }

    @Override
    @Transactional
    public void incrementViewCount(Long id) {
        videoRepository.incrementViewCount(id);
    }

    // ===== PRIVATE HELPERS =====

    private PageResponse<VideoResponse> mapToPageResponse(Page<Video> page) {
        return PageResponse.<VideoResponse>builder()
                .content(page.getContent().stream().map(this::mapToVideoResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    private VideoResponse mapToVideoResponse(Video video) {
        long commentCount = commentRepository.countActiveByVideoId(video.getId());

        UserResponse uploaderResponse = UserResponse.builder()
                .id(video.getUploadedBy().getId())
                .firstName(video.getUploadedBy().getFirstName())
                .lastName(video.getUploadedBy().getLastName())
                .email(video.getUploadedBy().getEmail())
                .role(video.getUploadedBy().getRole())
                .build();

        return VideoResponse.builder()
                .id(video.getId())
                .title(video.getTitle())
                .description(video.getDescription())
                .filePath(fileStorageService.getFileUrl(video.getFilePath()))
                .thumbnailPath(fileStorageService.getFileUrl(video.getThumbnailPath()))
                .durationSeconds(video.getDurationSeconds())
                .fileSizeBytes(video.getFileSizeBytes())
                .status(video.getStatus())
                .difficultyLevel(video.getDifficultyLevel())
                .viewCount(video.getViewCount())
                .questId(video.getQuest() != null ? video.getQuest().getId() : null)
                .questTitle(video.getQuest() != null ? video.getQuest().getTitle() : null)
                .uploadedBy(uploaderResponse)
                .commentCount(commentCount)
                .createdAt(video.getCreatedAt())
                .updatedAt(video.getUpdatedAt())
                .build();
    }
}
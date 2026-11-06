package com.mathweb.service.impl;

import com.mathweb.dto.request.UpdateVideoRequest;
import com.mathweb.dto.request.VideoUploadRequest;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.dto.response.VideoResponse;
import com.mathweb.entity.Category;
import com.mathweb.entity.Quest;
import com.mathweb.entity.User;
import com.mathweb.entity.Video;
import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.Role;
import com.mathweb.enums.VideoStatus;
import com.mathweb.event.VideoPublishedEvent;
import com.mathweb.exception.ForbiddenException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.mapper.VideoMapper;
import com.mathweb.repository.*;
import com.mathweb.service.FileStorageService;
import com.mathweb.service.VideoService;
import com.mathweb.service.VideoStream;
import com.mathweb.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VideoServiceImpl implements VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoServiceImpl.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final QuestRepository questRepository;
    private final CommentRepository commentRepository;
    private final FileStorageService fileStorageService;
    private final CategoryRepository categoryRepository;
    private final VideoMapper videoMapper;
    private final VideoMetadataService videoMetadataService;
    private final ApplicationEventPublisher events;

    public VideoServiceImpl(VideoRepository videoRepository,
                            UserRepository userRepository,
                            QuestRepository questRepository,
                            CommentRepository commentRepository,
                            FileStorageService fileStorageService,
                            CategoryRepository categoryRepository,
                            VideoMapper videoMapper,
                            VideoMetadataService videoMetadataService, ApplicationEventPublisher events) {
        this.videoRepository = videoRepository;
        this.userRepository = userRepository;
        this.questRepository = questRepository;
        this.commentRepository = commentRepository;
        this.fileStorageService = fileStorageService;
        this.categoryRepository = categoryRepository;
        this.videoMapper = videoMapper;
        this.videoMetadataService = videoMetadataService;
        this.events = events;
    }

    // ===== UPLOAD =====

    @Override
    @Transactional
    public VideoResponse uploadVideo(MultipartFile file,
                                     VideoUploadRequest request,
                                     Long uploaderId) {
        User uploader = userRepository.findById(uploaderId)
                .orElseThrow(() -> new ResourceNotFoundException("User", uploaderId));

        // Resolve everything that can fail BEFORE writing the file to disk,
        // so a bad quest/category id never leaves an orphan file behind.
        Quest quest = null;
        if (request.getQuestId() != null) {
            quest = questRepository.findById(request.getQuestId())
                    .orElseThrow(() -> new ResourceNotFoundException("Quest", request.getQuestId()));
        }
        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));
        }

        String filePath = fileStorageService.storeVideo(file);
        try {
            Video video = Video.builder()
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .filePath(filePath)
                    .mimeType(file.getContentType())
                    .fileSizeBytes(file.getSize())
                    .difficultyLevel(request.getDifficultyLevel())
                    .status(VideoStatus.PUBLISHED)
                    .free(Boolean.TRUE.equals(request.getFree()))
                    .uploadedBy(uploader)
                    .viewCount(0L)
                    .build();
            video.setQuest(quest);
            video.setCategory(category);
            video.setDurationSeconds(videoMetadataService.extractDuration(filePath));

            videoRepository.saveAndFlush(video);
            events.publishEvent(new VideoPublishedEvent(video.getId(), video.getTitle()));
            log.info("Video uploaded: {} by user {}", video.getTitle(), uploaderId);
            return mapToVideoResponse(video);
        } catch (RuntimeException e) {
            deleteQuietly(filePath);   // DB work is rolled back, so remove the file too
            throw e;
        }
    }

    // ===== READ (students and everyone: published only) =====

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
        PageRequest pageRequest = pageRequest(page, size, Sort.by("createdAt").descending());

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
    public PageResponse<VideoResponse> getAllVideos(int page, int size, DifficultyLevel level,
                                                    Long categoryId, String sort) {
        Sort s = switch (sort == null ? "newest" : sort) {
            case "oldest"   -> Sort.by("createdAt").ascending();
            case "popular"  -> Sort.by("viewCount").descending();
            case "shortest" -> Sort.by("durationSeconds").ascending();
            case "longest"  -> Sort.by("durationSeconds").descending();
            default         -> Sort.by("createdAt").descending();
        };

        Page<Video> videoPage = videoRepository.findFiltered(
                VideoStatus.PUBLISHED, categoryId, level, pageRequest(page, size, s));
        return mapToPageResponse(videoPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VideoResponse> searchVideos(String keyword, int page, int size) {
        Page<Video> videoPage = videoRepository.searchByTitle(
                normalizeKeyword(keyword),
                pageRequest(page, size, Sort.by("createdAt").descending()));
        return mapToPageResponse(videoPage);
    }

    // ===== READ (staff: every status) =====

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VideoResponse> getVideosForStaff(int page, int size,
                                                         VideoStatus status,
                                                         String keyword,
                                                         boolean mine,
                                                         Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (!isStaff(user)) {
            throw new ForbiddenException("Staff access required");
        }

        Page<Video> videoPage = videoRepository.findForStaff(
                status,
                mine ? userId : null,
                normalizeKeyword(keyword),
                pageRequest(page, size, Sort.by("createdAt").descending()));
        return mapToPageResponse(videoPage);
    }

    // ===== UPDATE =====

    @Override
    @Transactional
    public VideoResponse updateVideo(Long id, UpdateVideoRequest request, Long userId) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video", id));
        assertCanModify(video, userId);
        VideoStatus before = video.getStatus();

        if (request.getTitle() != null) video.setTitle(request.getTitle());
        if (request.getDescription() != null) video.setDescription(request.getDescription());
        if (request.getDifficultyLevel() != null) video.setDifficultyLevel(request.getDifficultyLevel());
        if (request.getStatus() != null) video.setStatus(request.getStatus());
        if (request.getFree() != null) video.setFree(request.getFree());

        // A clear flag wins over a provided id.
        if (Boolean.TRUE.equals(request.getClearQuest())) {
            video.setQuest(null);
        } else if (request.getQuestId() != null) {
            Quest quest = questRepository.findById(request.getQuestId())
                    .orElseThrow(() -> new ResourceNotFoundException("Quest", request.getQuestId()));
            video.setQuest(quest);
        }

        if (Boolean.TRUE.equals(request.getClearCategory())) {
            video.setCategory(null);
        } else if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));
            video.setCategory(category);
        }

        videoRepository.save(video);
        if (before != VideoStatus.PUBLISHED && video.getStatus() == VideoStatus.PUBLISHED) {
            events.publishEvent(new VideoPublishedEvent(video.getId(), video.getTitle()));
        }
        return mapToVideoResponse(video);
    }

    @Override
    @Transactional
    public VideoResponse uploadThumbnail(Long videoId,
                                         MultipartFile thumbnail,
                                         Long userId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("Video", videoId));
        assertCanModify(video, userId);

        FileUtil.validateImage(thumbnail);

        // Store the new file first; remove the old one only after the new one is saved.
        String oldPath = video.getThumbnailPath();
        String newPath = fileStorageService.storeImage(thumbnail);
        video.setThumbnailPath(newPath);
        videoRepository.save(video);
        if (oldPath != null) {
            deleteQuietly(oldPath);
        }

        log.info("Thumbnail uploaded for video {}", videoId);
        return mapToVideoResponse(video);
    }

    // ===== DELETE =====

    @Override
    @Transactional
    public void deleteVideo(Long id, Long userId) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video", id));
        assertCanModify(video, userId);

        String filePath = video.getFilePath();
        String thumbnailPath = video.getThumbnailPath();

        // Delete the row first and flush, so a database failure (for example a
        // foreign key) happens before any file is touched.
        videoRepository.delete(video);
        videoRepository.flush();

        deleteQuietly(filePath);
        deleteQuietly(thumbnailPath);
        log.info("Video deleted: {} by user {}", id, userId);
    }

    @Override
    @Transactional
    public void incrementViewCount(Long id) {
        videoRepository.incrementViewCount(id);
    }

    // ===== WATCH =====

    @Override
    @Transactional(readOnly = true)
    public VideoResponse getVideoById(Long id, Long userId) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video", id));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!isStaff(user) && video.getStatus() != VideoStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Video", id);   // hide drafts from students
        }
        return mapToVideoResponse(video, canWatch(video, user));
    }

    @Override
    @Transactional(readOnly = true)
    public VideoStream openStream(Long id, Long userId) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video", id));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!isStaff(user) && video.getStatus() != VideoStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Video", id);
        }
        if (!canWatch(video, user)) {
            throw new ForbiddenException("An active subscription is required to watch this video");
        }
        Resource resource = fileStorageService.loadAsResource(video.getFilePath());
        return new VideoStream(resource, video.getMimeType());
    }

    // ===== PRIVATE HELPERS =====

    private PageRequest pageRequest(int page, int size, Sort sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, sort);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) return "";
        String k = keyword.trim();
        return k.length() > MAX_KEYWORD_LENGTH ? k.substring(0, MAX_KEYWORD_LENGTH) : k;
    }

    /** Admin may touch any video; a teacher only the videos they uploaded. */
    private void assertCanModify(Video video, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isUploader = video.getUploadedBy() != null
                && video.getUploadedBy().getId().equals(userId);
        if (!isAdmin && !isUploader) {
            throw new ForbiddenException("You are not allowed to modify this video");
        }
    }

    private void deleteQuietly(String path) {
        if (path == null) return;
        try {
            fileStorageService.deleteFile(path);
        } catch (RuntimeException e) {
            log.warn("Could not delete file {}: {}", path, e.getMessage());
        }
    }

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
        return mapToVideoResponse(video, false);
    }

    private VideoResponse mapToVideoResponse(Video video, boolean includeStreamUrl) {
        long commentCount = commentRepository.countActiveByVideoId(video.getId());
        VideoResponse response = videoMapper.toResponse(video);
        response.setThumbnailPath(fileStorageService.getFileUrl(video.getThumbnailPath()));
        response.setCommentCount(commentCount);
        if (response.getUploadedBy() != null) {
            // getFileUrl accepts a stored path or an existing URL and returns a URL either way
            response.getUploadedBy().setAvatarUrl(
                    fileStorageService.getFileUrl(response.getUploadedBy().getAvatarUrl()));
        }
        if (includeStreamUrl) {
            response.setStreamUrl("/api/videos/" + video.getId() + "/stream");
        }
        return response;
    }

    private boolean isStaff(User user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.TEACHER;
    }

    private boolean canWatch(Video video, User user) {
        return isStaff(user) || video.isFree() || user.hasActiveSubscription();
    }
}
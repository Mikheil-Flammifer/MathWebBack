package com.mathweb.util;

import com.mathweb.exception.FileStorageException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

public class FileUtil {

    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/webm", "video/ogg", "video/avi", "video/mkv"
    );

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private static final long MAX_VIDEO_SIZE = 2L * 1024 * 1024 * 1024; // 2GB
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;        // 10MB

    private FileUtil() {}

    public static void validateVideo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Video file is empty");
        }
        if (file.getSize() > MAX_VIDEO_SIZE) {
            throw new FileStorageException("Video file exceeds 2GB limit");
        }
        if (!ALLOWED_VIDEO_TYPES.contains(file.getContentType())) {
            throw new FileStorageException(
                    "Invalid video type. Allowed: mp4, webm, ogg, avi, mkv");
        }
    }

    public static void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Image file is empty");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new FileStorageException("Image file exceeds 10MB limit");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            throw new FileStorageException(
                    "Invalid image type. Allowed: jpeg, png, gif, webp");
        }
    }

    public static String generateUniqueFilename(String originalFilename) {
        String extension = getExtension(originalFilename);
        return UUID.randomUUID() + "." + extension;
    }

    public static String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "bin";
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
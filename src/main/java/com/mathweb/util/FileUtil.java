package com.mathweb.util;

import com.mathweb.exception.FileStorageException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FileUtil {

    // Only formats a browser can actually play. Browsers label avi and mkv as
    // video/x-msvideo and video/x-matroska anyway, so the old video/avi and
    // video/mkv entries never matched a real upload.
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/webm", "video/ogg"
    );

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp"
    );

    private static final Map<String, String> VIDEO_EXTENSIONS = Map.of(
            "video/mp4", "mp4",
            "video/webm", "webm",
            "video/ogg", "ogg"
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
        String type = file.getContentType();
        // Set.of(...).contains(null) throws NullPointerException, so check null first.
        if (type == null || !ALLOWED_VIDEO_TYPES.contains(type)) {
            throw new FileStorageException(
                    "Invalid video type. Allowed: mp4, webm, ogg");
        }
        // The content type is chosen by the client, so also check the file's own header.
        // This stops text/playlist files from being stored as "video" and then parsed by ffmpeg.
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            if (!hasVideoSignature(type, head)) {
                throw new FileStorageException("File content does not match a video type");
            }
        } catch (IOException e) {
            throw new FileStorageException("Could not read the video file");
        }
    }

    public static void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Image file is empty");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new FileStorageException("Image file exceeds 10MB limit");
        }
        String type = file.getContentType();
        if (type == null || !ALLOWED_IMAGE_TYPES.contains(type)) {
            throw new FileStorageException(
                    "Invalid image type. Allowed: jpeg, png, gif, webp");
        }
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            if (!hasImageSignature(type, head)) {
                throw new FileStorageException("File content does not match an image type");
            }
        } catch (IOException e) {
            throw new FileStorageException("Could not read the image file");
        }
    }

    public static String imageExtension(String contentType) {
        return contentType == null ? "bin" : IMAGE_EXTENSIONS.getOrDefault(contentType, "bin");
    }

    public static String videoExtension(String contentType) {
        return contentType == null ? "bin" : VIDEO_EXTENSIONS.getOrDefault(contentType, "bin");
    }

    private static boolean hasVideoSignature(String type, byte[] h) {
        if (h.length < 12) return false;
        return switch (type) {
            // MP4: size (4 bytes) then "ftyp"
            case "video/mp4" -> h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';
            // WebM: EBML header 1A 45 DF A3
            case "video/webm" -> (h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45
                    && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3;
            // Ogg: "OggS"
            case "video/ogg" -> h[0] == 'O' && h[1] == 'g' && h[2] == 'g' && h[3] == 'S';
            default -> false;
        };
    }

    private static boolean hasImageSignature(String type, byte[] h) {
        if (h.length < 12) return false;
        return switch (type) {
            case "image/jpeg" -> (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case "image/png" -> (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            case "image/gif" -> h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8';
            case "image/webp" -> h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            default -> false;
        };
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
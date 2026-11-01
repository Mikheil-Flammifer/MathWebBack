package com.mathweb.service.impl;

import com.mathweb.exception.FileStorageException;
import com.mathweb.service.FileStorageService;
import com.mathweb.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    @Value("${file.upload.videos-path}")
    private String videosPath;

    @Value("${file.upload.images-path}")
    private String imagesPath;

    @Value("${file.upload.base-path}")
    private String basePath;

    @Value("${app.base-url}")
    private String baseUrl;

    @Override
    public String storeVideo(MultipartFile file) {
        return storeFile(file, videosPath, "videos");
    }

    @Override
    public String storeImage(MultipartFile file) {
        return storeFile(file, imagesPath, "images");
    }

    @Override
    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isBlank()) return;
        try {
            Path base = Paths.get(basePath).toAbsolutePath().normalize();
            Path target = Paths.get(filePath).toAbsolutePath().normalize();
            if (!target.startsWith(base)) {
                log.warn("Refused to delete file outside the upload folder: {}", filePath);
                return;
            }
            if (Files.deleteIfExists(target)) {
                log.info("Deleted file: {}", target);
            }
        } catch (IOException | InvalidPathException e) {
            log.error("Could not delete file: {}", filePath, e);
        }
    }

    @Override
    public String getFileUrl(String filePath) {
        if (filePath == null) return null;

        String normalized = filePath.replace("\\", "/");
        int lastSlash = normalized.lastIndexOf('/');
        int prevSlash = lastSlash > 0 ? normalized.lastIndexOf('/', lastSlash - 1) : -1;

        String relative = normalized.substring(prevSlash + 1);

        return baseUrl + "/uploads/" + relative;
    }

    @Override
    public Resource loadAsResource(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new FileStorageException("File not found");
        }
        try {
            Path base = Paths.get(basePath).toAbsolutePath().normalize();
            Path target = Paths.get(filePath).toAbsolutePath().normalize();
            if (!target.startsWith(base) || !Files.isRegularFile(target)) {
                throw new FileStorageException("File not found");
            }
            return new FileSystemResource(target);
        } catch (InvalidPathException e) {
            throw new FileStorageException("File not found");
        }
    }

    // ===== PRIVATE HELPERS =====

    private String storeFile(MultipartFile file, String storagePath, String type) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("File is empty or null");
        }

        if ("videos".equals(type)) {
            FileUtil.validateVideo(file);
        } else {
            FileUtil.validateImage(file);
        }

        Path targetPath = null;
        try {
            Path uploadDir = Paths.get(storagePath);
            Files.createDirectories(uploadDir);

            // Extension comes from the validated content type, never from the client's file name.
            String ext = "videos".equals(type)
                    ? FileUtil.videoExtension(file.getContentType())
                    : FileUtil.imageExtension(file.getContentType());
            String uniqueFilename = UUID.randomUUID() + "." + ext;

            targetPath = uploadDir.resolve(uniqueFilename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetPath);   // fails if the name already exists
            }

            String storedPath = storagePath + "/" + uniqueFilename;
            log.info("Stored {} file: {} ({})",
                    type, storedPath,
                    FileUtil.formatFileSize(file.getSize()));

            return storedPath;

        } catch (IOException e) {
            // Remove a half-written file (disk full, client aborted, and so on).
            if (targetPath != null) {
                try {
                    Files.deleteIfExists(targetPath);
                } catch (IOException ignored) {
                    // nothing more we can do
                }
            }
            // Details stay in the log: IOException messages contain server paths.
            log.error("Failed to store {} file", type, e);
            throw new FileStorageException("Failed to store file");
        }
    }
}
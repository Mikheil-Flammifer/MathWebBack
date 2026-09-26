package com.mathweb.service.impl;

import com.mathweb.exception.FileStorageException;
import com.mathweb.service.FileStorageService;
import com.mathweb.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    @Value("${file.upload.videos-path}")
    private String videosPath;

    @Value("${file.upload.images-path}")
    private String imagesPath;

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
        try {
            Path path = Paths.get(filePath);
            Files.deleteIfExists(path);
            log.info("Deleted file: {}", filePath);
        } catch (IOException e) {
            log.error("Could not delete file: {}", filePath, e);
        }
    }

    @Override
    public String getFileUrl(String filePath) {
        if (filePath == null) return null;
        // Convert local path to URL
        String relativePath = filePath.replace("\\", "/");
        if (relativePath.startsWith("./")) {
            relativePath = relativePath.substring(2);
        }
        return baseUrl + "/" + relativePath;
    }

    // ===== PRIVATE HELPERS =====

    private String storeFile(MultipartFile file, String storagePath, String type) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("File is empty or null");
        }

        // Validate based on type
        if ("videos".equals(type)) {
            FileUtil.validateVideo(file);
        } else {
            FileUtil.validateImage(file);
        }

        try {
            Path uploadDir = Paths.get(storagePath);
            Files.createDirectories(uploadDir);

            // Use FileUtil for unique filename
            String uniqueFilename = FileUtil.generateUniqueFilename(
                    file.getOriginalFilename());

            Path targetPath = uploadDir.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetPath,
                    StandardCopyOption.REPLACE_EXISTING);

            String storedPath = storagePath + "/" + uniqueFilename;
            log.info("Stored {} file: {} ({})",
                    type, storedPath,
                    FileUtil.formatFileSize(file.getSize()));

            return storedPath;

        } catch (IOException e) {
            throw new FileStorageException(
                    "Failed to store file: " + e.getMessage());
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "bin";
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
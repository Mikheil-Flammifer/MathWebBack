package com.mathweb.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String storeVideo(MultipartFile file);
    String storeImage(MultipartFile file);
    void deleteFile(String filePath);
    String getFileUrl(String filePath);
}
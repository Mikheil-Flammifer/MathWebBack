package com.mathweb.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String storeVideo(MultipartFile file);
    String storeImage(MultipartFile file);
    void deleteFile(String filePath);
    String getFileUrl(String filePath);
    Resource loadAsResource(String filePath);
}
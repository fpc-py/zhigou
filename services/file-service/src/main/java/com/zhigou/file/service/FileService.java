package com.zhigou.file.service;

import com.zhigou.file.dto.UploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    UploadResponse upload(Long userId, MultipartFile file);

    String getSignedUrl(Long userId, String fileId);

    void delete(Long userId, String fileId);
}
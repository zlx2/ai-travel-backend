package com.sora.aitravel.service;

import com.sora.aitravel.dto.response.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {
    FileUploadResponse upload(MultipartFile file, String bizType);
}

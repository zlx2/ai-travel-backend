package com.sora.aitravel.service;

import com.sora.aitravel.dto.response.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传服务接口。
 *
 * <p>提供文件上传到云存储（腾讯云 COS）的功能， 支持不同类型文件（头像、游记封面等）的上传。
 */
public interface FileService {
    /**
     * 上传文件。
     *
     * @param file 待上传的文件
     * @param bizType 业务类型，用于区分不同的文件存储目录，如 "avatar"、"note_cover"
     * @return 上传结果，包含文件的访问 URL
     */
    FileUploadResponse upload(MultipartFile file, String bizType);
}

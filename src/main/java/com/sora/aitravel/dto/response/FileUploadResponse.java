package com.sora.aitravel.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传响应 DTO。
 *
 * @param url 文件的访问 URL
 * @param objectKey 文件在对象存储中的唯一键
 * @param fileName 上传时的原始文件名
 * @param size 文件大小（字节）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {

    private String url;
    private String objectKey;
    private String fileName;
    private Long size;
}

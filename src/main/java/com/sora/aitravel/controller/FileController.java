package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.R;
import com.sora.aitravel.dto.response.FileUploadResponse;
import com.sora.aitravel.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传控制器。
 *
 * <p>接口前缀：/api/files
 *
 * <p>请求方式：POST，multipart/form-data
 *
 * <p>权限要求：所有接口均需登录（@SaCheckLogin）
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    /**
     * 上传文件（需登录）。
     *
     * @param file 上传的 Multipart 文件
     * @param bizType 业务类型（如 avatar、note_cover），用于区分 COS 存储目录
     * @return 上传成功返回文件的 URL、对象键、文件名和大小（FileUploadResponse）
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<FileUploadResponse> upload(
            @RequestPart("file") MultipartFile file, @RequestParam String bizType) {
        return R.ok(fileService.upload(file, bizType));
    }
}

package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.response.FileUploadResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@SaCheckLogin
@RestController
@RequestMapping("/api/files")
public class FileController {
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<FileUploadResponse> upload(
            @RequestPart MultipartFile file, @RequestParam String bizType) {
        return ScaffoldResponses.notImplemented();
    }
}

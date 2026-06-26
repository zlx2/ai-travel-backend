package com.sora.aitravel.service.impl;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.common.utils.LoginUserUtils;
import com.sora.aitravel.config.CosProperties;
import com.sora.aitravel.dto.response.FileUploadResponse;
import com.sora.aitravel.entity.FileResource;
import com.sora.aitravel.mapper.FileResourceMapper;
import com.sora.aitravel.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static com.sora.aitravel.common.enums.ErrorCode.*;

/**
 * 文件上传服务实现。
 *
 * <p>将用户上传的文件存储到腾讯云 COS，并将记录写入 file_resource 表。
 * 一期支持 avatar（头像）和 note_cover（游记封面）两种业务类型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final CosProperties cosProperties;
    private final COSClient cosClient;
    private final FileResourceMapper fileResourceMapper;

    /** 最大文件大小：5 MB。 */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;

    /** 允许的文件后缀（小写，不含点）。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    /** 允许的 Content-Type。 */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    /** 允许的业务类型。 */
    private static final Set<String> ALLOWED_BIZ_TYPES = Set.of("avatar", "note_cover");

    @Override
    public FileUploadResponse upload(MultipartFile file, String bizType) {
        // 1. 校验 bizType
        if (bizType == null || !ALLOWED_BIZ_TYPES.contains(bizType)) {
            throw new BusinessException(FILE_SERVICE_ERROR, "不支持的业务类型：" + bizType);
        }

        // 2. 校验文件非空
        if (file == null || file.isEmpty()) {
            throw new BusinessException(FILE_EMPTY);
        }

        // 3. 校验文件大小（最大 5 MB）
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(FILE_SIZE_EXCEEDED);
        }

        // 4. 校验文件类型
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        }
        String contentType = file.getContentType();
        if (!ALLOWED_EXTENSIONS.contains(ext)
                || (contentType != null && !ALLOWED_CONTENT_TYPES.contains(contentType))) {
            throw new BusinessException(FILE_TYPE_NOT_SUPPORTED);
        }

        // 5. 获取当前登录用户
        Long userId = LoginUserUtils.getUserId();

        // 6. 构建 COS objectKey：bizType/userId/yyyy/MM/dd/uuid.ext
        LocalDate now = LocalDate.now();
        String datePath = String.format("%d/%02d/%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        String uuid = UUID.randomUUID().toString();
        String objectKey = bizType + "/" + userId + "/" + datePath + "/" + uuid + "." + ext;

        // 7. 上传文件到腾讯云 COS
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(file.getSize());

            PutObjectRequest putRequest = new PutObjectRequest(
                    cosProperties.getBucket(), objectKey, file.getInputStream(), metadata);
            cosClient.putObject(putRequest);
        } catch (Exception e) {
            log.error("COS upload failed: objectKey={}, size={}", objectKey, file.getSize(), e);
            throw new BusinessException(FILE_UPLOAD_FAILED);
        }

        // 8. 构建访问 URL
        String url = buildUrl(objectKey);

        // 9. 写入 file_resource 表
        FileResource resource = new FileResource();
        resource.setUserId(userId);
        resource.setBizType(bizType);
        resource.setFileName(originalFilename);
        resource.setObjectKey(objectKey);
        resource.setUrl(url);
        resource.setContentType(contentType);
        resource.setSize(file.getSize());
        resource.setStatus(1);
        resource.setCreateTime(LocalDateTime.now());
        resource.setUpdateTime(LocalDateTime.now());
        resource.setDeleted(0);
        fileResourceMapper.insert(resource);

        // 10. 返回响应
        return new FileUploadResponse(url, objectKey, originalFilename, file.getSize());
    }

    /**
     * 根据 objectKey 构建 COS 可访问 URL。
     *
     * <p>优先使用自定义 domain（如已配置），否则使用默认 COS 域名。
     */
    private String buildUrl(String objectKey) {
        String domain = cosProperties.getDomain();
        if (domain != null && !domain.isBlank()) {
            return domain.replaceAll("/+$", "") + "/" + objectKey;
        }
        return String.format("https://%s.cos.%s.myqcloud.com/%s",
                cosProperties.getBucket(), cosProperties.getRegion(), objectKey);
    }
}

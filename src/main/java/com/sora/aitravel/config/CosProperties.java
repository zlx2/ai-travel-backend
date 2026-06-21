package com.sora.aitravel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 腾讯云对象存储（COS）配置属性。
 * <p>
 * 从配置文件 app.cos.* 中读取 COS 相关参数。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.cos")
public class CosProperties {
    /** 腾讯云 API 密钥 ID。 */
    private String secretId;
    /** 腾讯云 API 密钥 Key。 */
    private String secretKey;
    /** COS 存储桶名称。 */
    private String bucket;
    /** COS 存储桶所属地域，如 ap-guangzhou。 */
    private String region;
    /** COS 自定义访问域名。 */
    private String domain;
}

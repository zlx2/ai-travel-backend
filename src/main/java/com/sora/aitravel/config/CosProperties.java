package com.sora.aitravel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.cos")
public class CosProperties {
    private String secretId;
    private String secretKey;
    private String bucket;
    private String region;
    private String domain;
}

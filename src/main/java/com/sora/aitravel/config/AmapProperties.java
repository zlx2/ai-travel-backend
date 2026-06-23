package com.sora.aitravel.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 高德开放平台配置属性。
 *
 * <p>统一从 app.amap.* 读取高德相关配置，避免在不同业务 Client 中硬编码网关地址、超时时间或密钥。
 * 后续酒店、景点、租车、地址解析等能力都应复用该配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.amap")
public class AmapProperties {
    /** 高德 Web Service API Key。 */
    private String apiKey;

    /** 高德 Web Service 网关地址。 */
    private String baseUrl;

    /** 高德 HTTP 请求超时时间。 */
    private Duration timeout;
}

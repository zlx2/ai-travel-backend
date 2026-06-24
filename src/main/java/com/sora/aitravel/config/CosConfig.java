package com.sora.aitravel.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云对象存储（COS）客户端配置。
 *
 * <p>根据注入的 {@link CosProperties} 创建 COSClient 实例， Spring 容器关闭时自动调用 shutdown() 释放连接资源。
 */
@Configuration
public class CosConfig {

    @Bean(destroyMethod = "shutdown")
    public COSClient cosClient(CosProperties properties) {
        // 使用 SecretId 和 SecretKey 构建凭证
        COSCredentials credentials =
                new BasicCOSCredentials(properties.getSecretId(), properties.getSecretKey());
        // 配置地域信息
        ClientConfig clientConfig = new ClientConfig(new Region(properties.getRegion()));
        return new COSClient(credentials, clientConfig);
    }
}

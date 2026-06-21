package com.sora.aitravel.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 创建腾讯云 COS 客户端，应用关闭时由 Spring 负责释放连接资源。 */
@Configuration
public class CosConfig {

    @Bean(destroyMethod = "shutdown")
    public COSClient cosClient(CosProperties properties) {
        COSCredentials credentials =
                new BasicCOSCredentials(properties.getSecretId(), properties.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(properties.getRegion()));
        return new COSClient(credentials, clientConfig);
    }
}

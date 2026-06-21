package com.sora.aitravel.config;

import java.util.TimeZone;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson JSON 序列化配置。
 * <p>
 * 设置默认日期格式为 "yyyy-MM-dd HH:mm:ss"，时区为 Asia/Shanghai（东八区）。
 * </p>
 */
@Configuration
public class JacksonConfig {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer dateTimeCustomizer() {
        return builder ->
                builder.simpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .timeZone(TimeZone.getTimeZone("Asia/Shanghai"));
    }
}

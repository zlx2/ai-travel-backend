package com.sora.aitravel.config;

import java.util.TimeZone;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer dateTimeCustomizer() {
        return builder ->
                builder.simpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .timeZone(TimeZone.getTimeZone("Asia/Shanghai"));
    }
}

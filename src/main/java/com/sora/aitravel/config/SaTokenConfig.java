package com.sora.aitravel.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // SaInterceptor 负责解析 Controller 上的 @SaCheckLogin / @SaCheckRole 注解。
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }
}

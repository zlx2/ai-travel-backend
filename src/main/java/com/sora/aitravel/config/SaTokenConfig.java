package com.sora.aitravel.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 权限认证框架配置。
 * <p>
 * 注册 Sa-Token 拦截器，使其能够解析 Controller 上的
 * &#64;SaCheckLogin（登录校验）和 &#64;SaCheckRole（角色校验）注解。
 * </p>
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // SaInterceptor 负责解析 Controller 上的 @SaCheckLogin / @SaCheckRole 注解。
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }
}

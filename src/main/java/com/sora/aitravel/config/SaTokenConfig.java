package com.sora.aitravel.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 权限认证框架配置。
 *
 * <p>注册 Sa-Token 拦截器，使其能够解析 Controller 上的 &#64;SaCheckLogin（登录校验）和 &#64;SaCheckRole（角色校验）注解。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // SaInterceptor 负责解析 Controller 上的 @SaCheckLogin / @SaCheckRole 注解。
        registry.addInterceptor(asyncAwareSaInterceptor()).addPathPatterns("/**");
    }

    private HandlerInterceptor asyncAwareSaInterceptor() {
        SaInterceptor delegate = new SaInterceptor();
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(
                    HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                if (DispatcherType.ASYNC.equals(request.getDispatcherType())) {
                    return true;
                }
                return delegate.preHandle(request, response, handler);
            }

            @Override
            public void afterCompletion(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    Object handler,
                    Exception ex)
                    throws Exception {
                if (!DispatcherType.ASYNC.equals(request.getDispatcherType())) {
                    delegate.afterCompletion(request, response, handler, ex);
                }
            }
        };
    }
}

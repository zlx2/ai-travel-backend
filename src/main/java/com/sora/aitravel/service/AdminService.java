package com.sora.aitravel.service;

/**
 * 管理员服务接口。
 *
 * <p>提供管理员权限校验相关功能。
 */
public interface AdminService {
    /**
     * 断言当前登录用户为管理员。
     *
     * <p>如果不是管理员则抛出 {@link cn.dev33.satoken.exception.NotRoleException}。
     */
    void assertAdmin();
}

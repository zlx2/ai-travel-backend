package com.sora.aitravel.dto.response;

/**
 * 登录响应 DTO。
 *
 * @param token 登录成功后颁发的身份令牌（Sa-Token）
 * @param user  当前登录用户的详细信息
 */
public record LoginResponse(String token, UserInfoResponse user) {}

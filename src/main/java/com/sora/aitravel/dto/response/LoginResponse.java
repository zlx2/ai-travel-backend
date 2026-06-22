package com.sora.aitravel.dto.response;

/**
 * 登录响应 DTO。
 *
 * @param token 登录成功后颁发的身份令牌（Sa-Token）
 * @param user  当前登录用户的详细信息
 */
/**
 * 登录成功后的认证上下文。
 *
 * @param token Sa-Token 会话令牌，后续通过 Authorization: Bearer &lt;token&gt; 携带
 * @param user 可公开的当前用户资料，不包含密码哈希等内部字段
 */
public record LoginResponse(String token, UserInfoResponse user) {}

package com.sora.aitravel.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    SUCCESS(200, "success"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或 Token 无效"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "数据不存在"),
    CONFLICT(409, "数据冲突"),
    SYSTEM_ERROR(500, "系统错误"),
    USERNAME_OR_PASSWORD_ERROR(10001, "用户名或密码错误"),
    ACCOUNT_DISABLED(10002, "账号已被禁用"),
    USERNAME_EXISTS(10003, "用户名已存在"),
    EMAIL_EXISTS(10004, "邮箱已存在"),
    EMAIL_CODE_ERROR(10005, "邮箱验证码错误"),
    EMAIL_CODE_EXPIRED(10006, "邮箱验证码已过期"),
    TOKEN_INVALID(10007, "Token 无效"),
    NOT_LOGIN(10008, "未登录"),
    AI_SERVICE_ERROR(60001, "AI 服务调用失败"),
    AI_RESPONSE_FORMAT_ERROR(60002, "AI 返回格式错误"),
    AI_ANALYZE_ERROR(60003, "AI 需求解析失败"),
    AI_GENERATE_ERROR(60004, "AI 行程生成失败"),
    AI_CONVERSATION_EXPIRED(60005, "AI 会话不存在或已过期"),
    AI_CONTENT_SAFETY_ERROR(60006, "AI 内容安全校验失败"),
    FILE_EMPTY(70001, "文件不能为空"),
    FILE_TYPE_NOT_SUPPORTED(70002, "文件类型不支持"),
    FILE_SIZE_EXCEEDED(70003, "文件大小超限"),
    FILE_UPLOAD_FAILED(70004, "文件上传失败"),
    FILE_SERVICE_ERROR(70005, "文件服务异常");

    private final Integer code;
    private final String message;
}

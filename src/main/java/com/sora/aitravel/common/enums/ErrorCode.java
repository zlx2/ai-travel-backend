package com.sora.aitravel.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码枚举。
 * <p>
 * 统一管理系统中的所有错误码和提示信息，涵盖通用错误、认证错误、
 * AI 服务错误和文件上传错误等分类。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {
    /** 请求成功。 */
    SUCCESS(200, "success"),
    /** 参数错误。 */
    PARAM_ERROR(400, "参数错误"),
    /** 未登录或 Token 无效。 */
    UNAUTHORIZED(401, "未登录或 Token 无效"),
    /** 无权限访问。 */
    FORBIDDEN(403, "无权限"),
    /** 请求的数据不存在。 */
    NOT_FOUND(404, "数据不存在"),
    /** 数据冲突。 */
    CONFLICT(409, "数据冲突"),
    /** 系统内部错误。 */
    SYSTEM_ERROR(500, "系统错误"),

    // 认证相关错误码 (10001-10008)
    /** 用户名或密码错误。 */
    USERNAME_OR_PASSWORD_ERROR(10001, "用户名或密码错误"),
    /** 账号已被禁用。 */
    ACCOUNT_DISABLED(10002, "账号已被禁用"),
    /** 用户名已存在。 */
    USERNAME_EXISTS(10003, "用户名已存在"),
    /** 邮箱已存在。 */
    EMAIL_EXISTS(10004, "邮箱已存在"),
    /** 邮箱验证码错误。 */
    EMAIL_CODE_ERROR(10005, "邮箱验证码错误"),
    /** 邮箱验证码已过期。 */
    EMAIL_CODE_EXPIRED(10006, "邮箱验证码已过期"),
    /** Token 无效或已过期。 */
    TOKEN_INVALID(10007, "Token 无效"),
    /** 用户未登录。 */
    NOT_LOGIN(10008, "未登录"),

    // AI 服务相关错误码 (60001-60006)
    /** AI 服务调用失败。 */
    AI_SERVICE_ERROR(60001, "AI 服务调用失败"),
    /** AI 返回的数据格式不符合预期。 */
    AI_RESPONSE_FORMAT_ERROR(60002, "AI 返回格式错误"),
    /** AI 无法解析用户需求。 */
    AI_ANALYZE_ERROR(60003, "AI 需求解析失败"),
    /** AI 行程生成失败。 */
    AI_GENERATE_ERROR(60004, "AI 行程生成失败"),
    /** AI 会话不存在或已过期。 */
    AI_CONVERSATION_EXPIRED(60005, "AI 会话不存在或已过期"),
    /** AI 内容安全校验未通过。 */
    AI_CONTENT_SAFETY_ERROR(60006, "AI 内容安全校验失败"),

    // 文件上传相关错误码 (70001-70005)
    /** 上传文件不能为空。 */
    FILE_EMPTY(70001, "文件不能为空"),
    /** 上传文件类型不被支持。 */
    FILE_TYPE_NOT_SUPPORTED(70002, "文件类型不支持"),
    /** 上传文件大小超过限制。 */
    FILE_SIZE_EXCEEDED(70003, "文件大小超限"),
    /** 文件上传到云存储失败。 */
    FILE_UPLOAD_FAILED(70004, "文件上传失败"),
    /** 文件服务发生异常。 */
    FILE_SERVICE_ERROR(70005, "文件服务异常");

    /** 错误码编号。 */
    private final Integer code;
    /** 错误描述信息。 */
    private final String message;
}

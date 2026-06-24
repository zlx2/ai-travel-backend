package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求 DTO。
 *
 * @param account 登录账号（用户名或邮箱）
 * @param password 登录密码
 */
/**
 * 客户端提交的登录凭证。
 *
 * <p>{@code account} 同时支持用户名和邮箱，由认证服务查询时统一识别。密码只参与本次哈希 比对，不会以明文形式保存。
 *
 * @param account 用户名或邮箱，进入业务层后会移除首尾空白
 * @param password 用户输入的原始密码
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank private String account;
    @NotBlank private String password;
}

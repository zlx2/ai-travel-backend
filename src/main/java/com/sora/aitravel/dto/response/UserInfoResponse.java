package com.sora.aitravel.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息响应 DTO。
 *
 * @param id 用户 ID
 * @param username 用户名
 * @param nickname 用户昵称
 * @param email 邮箱地址
 * @param avatarUrl 头像 URL
 * @param role 角色（0-普通用户，1-管理员？）
 * @param status 账号状态（0-禁用，1-启用）
 * @param createTime 注册时间
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatarUrl;
    private Integer role;
    private Integer status;
    private String createTime;
}

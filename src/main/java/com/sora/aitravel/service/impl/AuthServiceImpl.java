package com.sora.aitravel.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sora.aitravel.common.constants.AuthConstants;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.common.utils.DateTimeUtils;
import com.sora.aitravel.dto.request.LoginRequest;
import com.sora.aitravel.dto.request.RegisterRequest;
import com.sora.aitravel.dto.request.ResetPasswordRequest;
import com.sora.aitravel.dto.response.LoginResponse;
import com.sora.aitravel.dto.response.UserInfoResponse;
import com.sora.aitravel.entity.SysUser;
import com.sora.aitravel.mapper.SysUserMapper;
import com.sora.aitravel.service.AuthService;
import com.sora.aitravel.service.EmailCodeService;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务实现
 * 提供用户注册、登录、登出、密码重置等认证相关功能
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String REGISTER_SCENE = "register";

    private final SysUserMapper userMapper;
    private final EmailCodeService emailCodeService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户注册
     * 校验用户名和邮箱唯一性，验证邮箱验证码，创建用户并核销验证码
     *
     * @param request 注册请求
     * @return 用户ID
     */
    @Override
    @Transactional
    public Long register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        ensureUsernameAvailable(username);
        ensureEmailAvailable(email);
        emailCodeService.verify(email, REGISTER_SCENE, request.getEmailCode());

        LocalDateTime now = LocalDateTime.now();
        SysUser user =
                SysUser.builder()
                        .username(username)
                        .passwordHash(passwordEncoder.encode(request.getPassword()))
                        .email(email)
                        .nickname(username)
                        .role(AuthConstants.USER_ROLE)
                        .status(1)
                        .createTime(now)
                        .updateTime(now)
                        .deleted(0)
                        .build();
        userMapper.insert(user);
        emailCodeService.remove(email, REGISTER_SCENE);
        return user.getId();
    }

    /**
     * 用户登录
     * 支持用户名或邮箱登录，校验密码，更新登录时间，生成登录令牌
     *
     * @param request 登录请求
     * @return 登录响应，包含令牌和用户信息
     */
    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String account = request.getAccount().trim();
        SysUser user =
                userMapper.selectOne(
                        new LambdaQueryWrapper<SysUser>()
                                .and(
                                        wrapper ->
                                                wrapper.eq(SysUser::getUsername, account)
                                                        .or()
                                                        .eq(SysUser::getEmail, account))
                                .last("LIMIT 1"));
        if (user == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        LocalDateTime now = LocalDateTime.now();
        user.setLastLoginTime(now);
        user.setUpdateTime(now);
        userMapper.updateById(user);
        StpUtil.login(user.getId());
        return new LoginResponse(StpUtil.getTokenValue(), toResponse(user));
    }

    /**
     * 用户登出
     * 清除当前用户的登录状态
     */
    @Override
    public void logout() {
        StpUtil.logout();
    }

    /**
     * 重置密码
     * 验证邮箱验证码，更新密码，强制用户所有会话下线，核销验证码
     *
     * @param request 重置密码请求
     */
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        emailCodeService.verify(email, "reset_password", request.getEmailCode());

        SysUser user =
                userMapper.selectOne(
                        new LambdaQueryWrapper<SysUser>()
                                .eq(SysUser::getEmail, email)
                                .last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该邮箱未注册");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        StpUtil.logout(user.getId());
        emailCodeService.remove(email, "reset_password");
    }

    /**
     * 确保用户名可用
     *
     * @param username 用户名
     * @throws BusinessException 如果用户名已存在
     */
    private void ensureUsernameAvailable(String username) {
        if (userMapper.selectCount(
                        new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username))
                > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
    }

    /**
     * 确保邮箱可用
     *
     * @param email 邮箱地址
     * @throws BusinessException 如果邮箱已被注册
     */
    private void ensureEmailAvailable(String email) {
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email))
                > 0) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }
    }

    /**
     * 将用户实体转换为响应对象
     *
     * @param user 用户实体
     * @return 用户信息响应
     */
    static UserInfoResponse toResponse(SysUser user) {
        return UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .createTime(DateTimeUtils.format(user.getCreateTime()))
                .build();
    }
}
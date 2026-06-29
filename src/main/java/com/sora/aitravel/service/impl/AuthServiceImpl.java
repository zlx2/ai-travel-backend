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

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String REGISTER_SCENE = "register";

    private final SysUserMapper userMapper;
    private final EmailCodeService emailCodeService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public Long register(RegisterRequest request) {
        // 先标准化唯一键，避免大小写或无意义空格绕过重复检查。
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        ensureUsernameAvailable(username);
        ensureEmailAvailable(email);
        // 唯一性确认后再校验邮箱所有权，错误类型对调用方更明确。
        emailCodeService.verify(email, REGISTER_SCENE, request.getEmailCode());

        LocalDateTime now = LocalDateTime.now();
        SysUser user =
                SysUser.builder()
                        .username(username)
                        // 数据库只保存带盐的单向哈希，永不持久化明文密码。
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
        // 注册成功后立即核销验证码，使其成为一次性凭证。
        emailCodeService.remove(email, REGISTER_SCENE);
        return user.getId();
    }

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
        // 两种失败使用同一提示，避免接口泄露某个账号是否已经注册。
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
        // 令牌有效期和并发登录策略统一由 Sa-Token 配置管理。
        StpUtil.login(user.getId());
        return new LoginResponse(StpUtil.getTokenValue(), toResponse(user));
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        // 先校验验证码，再查用户、改密码。
        emailCodeService.verify(email, "reset_password", request.getEmailCode());

        SysUser user =
                userMapper.selectOne(
                        new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email).last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该邮箱未注册");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        // 强制该用户所有会话下线，旧 Token 立即失效。
        StpUtil.logout(user.getId());
        // 核销验证码，使其成为一次性凭证。
        emailCodeService.remove(email, "reset_password");
    }

    private void ensureUsernameAvailable(String username) {
        if (userMapper.selectCount(
                        new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username))
                > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
    }

    private void ensureEmailAvailable(String email) {
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email))
                > 0) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }
    }

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

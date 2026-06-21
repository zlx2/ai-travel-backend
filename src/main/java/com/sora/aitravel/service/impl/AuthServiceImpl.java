package com.sora.aitravel.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sora.aitravel.common.constants.AuthConstants;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.common.utils.DateTimeUtils;
import com.sora.aitravel.dto.request.LoginRequest;
import com.sora.aitravel.dto.request.RegisterRequest;
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
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        ensureUsernameAvailable(username);
        ensureEmailAvailable(email);
        emailCodeService.verify(email, REGISTER_SCENE, request.emailCode());

        LocalDateTime now = LocalDateTime.now();
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEmail(email);
        user.setNickname(username);
        user.setRole(AuthConstants.USER_ROLE);
        user.setStatus(1);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        user.setDeleted(0);
        userMapper.insert(user);
        emailCodeService.remove(email, REGISTER_SCENE);
        return user.getId();
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String account = request.account().trim();
        SysUser user =
                userMapper.selectOne(
                        new LambdaQueryWrapper<SysUser>()
                                .and(
                                        wrapper ->
                                                wrapper.eq(SysUser::getUsername, account)
                                                        .or()
                                                        .eq(SysUser::getEmail, account))
                                .last("LIMIT 1"));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
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

    @Override
    public void logout() {
        StpUtil.logout();
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
        return new UserInfoResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus(),
                DateTimeUtils.format(user.getCreateTime()));
    }
}

package com.sora.aitravel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.common.utils.LoginUserUtils;
import com.sora.aitravel.dto.request.SendChangeEmailCodeRequest;
import com.sora.aitravel.dto.request.UpdateUserEmailRequest;
import com.sora.aitravel.dto.request.UpdateUserProfileRequest;
import com.sora.aitravel.dto.response.UserInfoResponse;
import com.sora.aitravel.entity.SysUser;
import com.sora.aitravel.mapper.SysUserMapper;
import com.sora.aitravel.service.EmailCodeService;
import com.sora.aitravel.service.UserService;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String CHANGE_EMAIL_SCENE = "change_email";

    private final SysUserMapper userMapper;
    private final EmailCodeService emailCodeService;

    @Override
    public UserInfoResponse getCurrentUser() {
        return AuthServiceImpl.toResponse(requireCurrentUser());
    }

    @Override
    @Transactional
    public void updateCurrentUser(UpdateUserProfileRequest request) {
        SysUser user = requireCurrentUser();
        user.setNickname(request.getNickname());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public void sendChangeEmailCode(SendChangeEmailCodeRequest request) {
        SysUser currentUser = requireCurrentUser();
        String newEmail = normalizeEmail(request.getNewEmail());

        // 新邮箱不能与当前邮箱相同
        if (newEmail.equals(currentUser.getEmail())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "新邮箱不能与当前邮箱相同");
        }

        // 新邮箱不能已被其他账号注册
        ensureEmailAvailable(newEmail);

        // 通过校验后发送验证码
        emailCodeService.send(newEmail, CHANGE_EMAIL_SCENE);
    }

    @Override
    @Transactional
    public void updateCurrentUserEmail(UpdateUserEmailRequest request) {
        SysUser currentUser = requireCurrentUser();
        String newEmail = normalizeEmail(request.getNewEmail());

        // 新邮箱不能与当前邮箱相同
        if (newEmail.equals(currentUser.getEmail())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "新邮箱不能与当前邮箱相同");
        }

        // 再次校验新邮箱未被其他账号注册（防止发送验证码后被别人占用）
        ensureEmailAvailable(newEmail);

        // 校验验证码
        emailCodeService.verify(newEmail, CHANGE_EMAIL_SCENE, request.getEmailCode());

        // 更新邮箱
        currentUser.setEmail(newEmail);
        currentUser.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(currentUser);

        // 核销验证码
        emailCodeService.remove(newEmail, CHANGE_EMAIL_SCENE);
    }

    private SysUser requireCurrentUser() {
        SysUser user = userMapper.selectById(LoginUserUtils.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void ensureEmailAvailable(String email) {
        Long count =
                userMapper.selectCount(
                        new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

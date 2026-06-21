package com.sora.aitravel.service.impl;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.common.utils.LoginUserUtils;
import com.sora.aitravel.dto.request.UpdateUserProfileRequest;
import com.sora.aitravel.dto.response.UserInfoResponse;
import com.sora.aitravel.entity.SysUser;
import com.sora.aitravel.mapper.SysUserMapper;
import com.sora.aitravel.service.UserService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;

    @Override
    public UserInfoResponse getCurrentUser() {
        return AuthServiceImpl.toResponse(requireCurrentUser());
    }

    @Override
    @Transactional
    public void updateCurrentUser(UpdateUserProfileRequest request) {
        SysUser user = requireCurrentUser();
        user.setNickname(request.nickname());
        user.setAvatarUrl(request.avatarUrl());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    private SysUser requireCurrentUser() {
        SysUser user = userMapper.selectById(LoginUserUtils.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}

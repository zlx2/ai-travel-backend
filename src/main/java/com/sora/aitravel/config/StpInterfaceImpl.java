package com.sora.aitravel.config;

import cn.dev33.satoken.stp.StpInterface;
import com.sora.aitravel.entity.SysUser;
import com.sora.aitravel.mapper.SysUserMapper;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sa-Token 权限/角色接口实现。
 *
 * <p>实现 {@link StpInterface} 接口，为 Sa-Token 提供当前登录用户的权限列表和角色列表。 一期仅区分用户角色（普通用户 role=1，管理员
 * role=2），没有细粒度权限表。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {
    private final SysUserMapper userMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 一期只区分用户角色，没有细粒度 permission 表。
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SysUser user = userMapper.selectById(Long.valueOf(String.valueOf(loginId)));
        // 根据用户角色字段返回角色标识，如 "2" 对应管理员
        // @SaCheckRole("2") 与 sys_user.role=2 对应管理员权限。
        return user == null ? Collections.emptyList() : List.of(String.valueOf(user.getRole()));
    }
}

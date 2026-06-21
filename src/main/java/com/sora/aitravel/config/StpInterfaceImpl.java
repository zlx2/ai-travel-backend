package com.sora.aitravel.config;

import cn.dev33.satoken.stp.StpInterface;
import com.sora.aitravel.entity.SysUser;
import com.sora.aitravel.mapper.SysUserMapper;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
        // @SaCheckRole("2") 与 sys_user.role=2 对应管理员权限。
        return user == null ? Collections.emptyList() : List.of(String.valueOf(user.getRole()));
    }
}

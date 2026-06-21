package com.sora.aitravel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sora.aitravel.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper 接口。
 * <p>
 * 对应实体 {@link SysUser}，操作数据库表 {@code sys_user}。
 * 提供系统用户的增删改查能力，包括按用户名/邮箱查询、用户状态管理等。
 * </p>
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {}

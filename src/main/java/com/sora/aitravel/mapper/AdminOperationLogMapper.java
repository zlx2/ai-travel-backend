package com.sora.aitravel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sora.aitravel.entity.AdminOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员操作日志 Mapper 接口。
 * <p>
 * 对应实体 {@link AdminOperationLog}，操作数据库表 {@code admin_operation_log}。
 * 提供管理员操作日志的增删改查能力，用于审计追踪和操作记录查询。
 * </p>
 */
@Mapper
public interface AdminOperationLogMapper extends BaseMapper<AdminOperationLog> {}

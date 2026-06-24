package com.sora.aitravel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sora.aitravel.entity.AiCallLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 调用日志 Mapper 接口。
 *
 * <p>对应实体 {@link AiCallLog}，操作数据库表 {@code ai_call_log}。 提供 AI 调用日志的增删改查能力，支持按用户、会话、场景等维度查询调用记录，
 * 用于用量统计和问题排查。
 */
@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {}

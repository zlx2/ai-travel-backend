package com.sora.aitravel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sora.aitravel.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 对话会话 Mapper 接口。
 * <p>
 * 对应实体 {@link AiConversation}，操作数据库表 {@code ai_conversation}。
 * 提供 AI 对话会话的增删改查能力，支持按会话 ID 和用户 ID 查询会话信息。
 * </p>
 */
@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {}

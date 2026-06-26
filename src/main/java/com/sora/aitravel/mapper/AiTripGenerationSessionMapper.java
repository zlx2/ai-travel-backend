package com.sora.aitravel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sora.aitravel.entity.AiTripGenerationSession;
import org.apache.ibatis.annotations.Mapper;

/** AI 行程生成会话 Mapper。 */
@Mapper
public interface AiTripGenerationSessionMapper extends BaseMapper<AiTripGenerationSession> {}

package com.sora.aitravel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sora.aitravel.entity.AiTripDayGeneration;
import org.apache.ibatis.annotations.Mapper;

/** AI 行程按天生成 Mapper。 */
@Mapper
public interface AiTripDayGenerationMapper extends BaseMapper<AiTripDayGeneration> {}

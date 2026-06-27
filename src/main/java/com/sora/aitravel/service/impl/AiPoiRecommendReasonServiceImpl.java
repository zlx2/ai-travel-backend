package com.sora.aitravel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sora.aitravel.entity.AiPoiRecommendReason;
import com.sora.aitravel.mapper.AiPoiRecommendReasonMapper;
import com.sora.aitravel.service.AiPoiRecommendReasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** POI 推荐理由服务实现。 */
@Service
@RequiredArgsConstructor
public class AiPoiRecommendReasonServiceImpl implements AiPoiRecommendReasonService {

    public static final String STATUS_AI_GENERATED = "AI_GENERATED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_EDITED = "EDITED";

    private final AiPoiRecommendReasonMapper mapper;

    @Override
    public AiPoiRecommendReason getUsableReason(
            String poiSource, String poiId, String promptVersion) {
        return mapper.selectOne(
                new LambdaQueryWrapper<AiPoiRecommendReason>()
                        .eq(AiPoiRecommendReason::getPoiSource, poiSource)
                        .eq(AiPoiRecommendReason::getPoiId, poiId)
                        .eq(AiPoiRecommendReason::getPromptVersion, promptVersion)
                        .in(
                                AiPoiRecommendReason::getStatus,
                                STATUS_EDITED,
                                STATUS_APPROVED,
                                STATUS_AI_GENERATED)
                        .last("LIMIT 1"));
    }

    @Override
    public AiPoiRecommendReason saveAiGenerated(
            String poiSource,
            String poiId,
            String poiName,
            String city,
            String poiType,
            String promptVersion,
            String reason,
            String modelName) {
        AiPoiRecommendReason existing = getUsableReason(poiSource, poiId, promptVersion);
        if (existing != null) {
            return existing;
        }
        AiPoiRecommendReason item =
                AiPoiRecommendReason.builder()
                        .poiSource(poiSource)
                        .poiId(poiId)
                        .poiName(poiName)
                        .city(city)
                        .poiType(poiType)
                        .promptVersion(promptVersion)
                        .reason(reason)
                        .status(STATUS_AI_GENERATED)
                        .modelName(modelName)
                        .build();
        mapper.insert(item);
        return item;
    }
}

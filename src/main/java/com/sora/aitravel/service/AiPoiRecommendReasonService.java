package com.sora.aitravel.service;

import com.sora.aitravel.entity.AiPoiRecommendReason;

/** POI 推荐理由服务。 */
public interface AiPoiRecommendReasonService {

    AiPoiRecommendReason getUsableReason(String poiSource, String poiId, String promptVersion);

    AiPoiRecommendReason saveAiGenerated(
            String poiSource,
            String poiId,
            String poiName,
            String city,
            String poiType,
            String promptVersion,
            String reason,
            String modelName);
}

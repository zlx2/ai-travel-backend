package com.sora.aitravel.service;

import com.sora.aitravel.dto.model.AmapApiResp;
import com.sora.aitravel.dto.model.poi.Poi;
import java.util.List;

public interface AmapApiService {

    AmapApiResp<List<Poi>> searchPoiText(
            String keywords,
            String types,
            String region,
            Boolean cityLimit,
            Integer pageSize,
            Integer pageNum,
            String showFields);
}

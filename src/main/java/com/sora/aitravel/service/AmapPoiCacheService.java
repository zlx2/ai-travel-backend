package com.sora.aitravel.service;

import com.sora.aitravel.dto.model.poi.Poi;
import java.util.List;

public interface AmapPoiCacheService {

    List<Poi> searchText(
            String keywords,
            String types,
            String region,
            boolean cityLimit,
            int pageSize,
            int pageNum,
            String showFields,
            String category);
}

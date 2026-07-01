package com.sora.aitravel.service;

import cn.hutool.json.JSONObject;
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

    JSONObject searchPoiTextRaw(
            String keywords,
            String types,
            String region,
            Boolean cityLimit,
            Integer pageSize,
            Integer pageNum,
            String showFields);

    JSONObject searchPoiAroundRaw(
            String location,
            String keywords,
            String types,
            String region,
            Boolean cityLimit,
            Integer radius,
            String sortrule,
            Integer pageSize,
            Integer pageNum,
            String showFields);

    JSONObject geocodeRaw(String address, String city);
}

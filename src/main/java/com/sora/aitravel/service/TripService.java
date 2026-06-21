package com.sora.aitravel.service;

import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.*;
import com.sora.aitravel.dto.response.*;

public interface TripService {
    Long save(SaveTripRequest request);

    PageResult<TripListItemResponse> listMy(
            Integer pageNum, Integer pageSize, String keyword, String destination);

    TripDetailResponse getDetail(Long id);

    void update(Long id, UpdateTripRequest request);

    void delete(Long id);
}

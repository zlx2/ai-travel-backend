package com.sora.aitravel.service;

import com.sora.aitravel.common.result.PageResult;
import com.sora.aitravel.dto.response.DestinationResponse;
import java.util.List;

public interface DestinationService {
    PageResult<DestinationResponse> list(String keyword, Integer pageNum, Integer pageSize);

    List<DestinationResponse> hot();
}

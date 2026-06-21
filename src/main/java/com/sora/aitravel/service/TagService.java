package com.sora.aitravel.service;

import com.sora.aitravel.dto.response.TagResponse;
import java.util.List;

public interface TagService {
    List<TagResponse> list(Integer type);
}

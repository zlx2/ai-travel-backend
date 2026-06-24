package com.sora.aitravel.service;

import com.sora.aitravel.dto.response.TagResponse;
import java.util.List;

/**
 * 标签服务接口。
 *
 * <p>提供游记标签的查询功能，支持按类型筛选标签。
 */
public interface TagService {
    /**
     * 根据类型获取标签列表。
     *
     * @param type 标签类型，如 1=目的地标签、2=风格标签
     * @return 标签列表
     */
    List<TagResponse> list(Integer type);
}

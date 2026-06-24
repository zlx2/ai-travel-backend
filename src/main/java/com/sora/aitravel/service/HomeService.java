package com.sora.aitravel.service;

import com.sora.aitravel.dto.response.HomeResponse;

/**
 * 首页服务接口。
 *
 * <p>聚合展示首页所需的数据，如热门目的地、推荐游记等。
 */
public interface HomeService {
    /**
     * 获取首页聚合数据。
     *
     * @return 首页数据，包含热门目的地、推荐游记等
     */
    HomeResponse aggregate();
}

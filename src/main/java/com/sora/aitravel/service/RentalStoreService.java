package com.sora.aitravel.service;

import com.sora.aitravel.common.enums.RentalStoreUsageEnum;
import com.sora.aitravel.dto.model.RentalStoreDTO;
import com.sora.aitravel.dto.model.RentalStoreResolveCommand;

/**
 * 租车服务点解析服务。
 *
 * <p>服务边界：只负责把“用户想在哪里取车/还车”解析为一个推荐地图服务点。它不查询车辆库存、不计算价格、不锁车、 不创建订单，也不处理支付。
 */
public interface RentalStoreService {

    /**
     * 根据 API 请求解析推荐租车服务点。
     *
     * @param request 结构化地点解析请求
     * @return 推荐服务点
     */
    RentalStoreDTO resolveRentalStore(RentalStoreResolveCommand command);

    /**
     * 根据明确的地点、城市和用途解析推荐租车服务点。
     *
     * <p>该方法用于 AI Tool、工作流节点或其他后端服务直接调用，避免为了内部调用再构造 HTTP 请求。
     *
     * @param targetName 目标地点名称
     * @param cityName 城市名称
     * @param usage 使用场景
     * @return 推荐服务点
     */
    RentalStoreDTO resolveRentalStore(
            String targetName, String cityName, RentalStoreUsageEnum usage);
}

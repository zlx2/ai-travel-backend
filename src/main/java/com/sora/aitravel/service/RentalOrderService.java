package com.sora.aitravel.service;

import com.sora.aitravel.dto.request.RentalOrderCreateRequest;
import com.sora.aitravel.dto.request.RentalOrderPayRequest;
import com.sora.aitravel.dto.response.RentalOrderResponse;
import java.util.List;

/**
 * 租车订单服务接口
 * 提供租车订单的创建、支付、查询、取消等业务操作
 */
public interface RentalOrderService {

    /**
     * 创建租车订单
     *
     * @param userId  用户ID
     * @param request 订单创建请求
     * @return 订单ID
     */
    Long create(Long userId, RentalOrderCreateRequest request);

    /**
     * 模拟支付租车订单
     *
     * @param userId  用户ID
     * @param id      订单ID
     * @param request 支付请求（可选）
     */
    void pay(Long userId, Long id, RentalOrderPayRequest request);

    /**
     * 获取用户的租车订单列表
     *
     * @param userId 用户ID
     * @return 租车订单列表
     */
    List<RentalOrderResponse> listMy(Long userId);

    /**
     * 获取租车订单详情
     *
     * @param userId 用户ID
     * @param id     订单ID
     * @return 租车订单详情
     */
    RentalOrderResponse get(Long userId, Long id);

    /**
     * 取消租车订单
     *
     * @param userId 用户ID
     * @param id     订单ID
     */
    void cancel(Long userId, Long id);
}
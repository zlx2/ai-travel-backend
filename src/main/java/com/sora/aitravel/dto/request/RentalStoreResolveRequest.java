package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 租车服务点解析请求。
 *
 * <p>该请求只用于把用户输入的地点解析为一个推荐取还车服务点，不包含车型、库存、价格、订单等信息。
 *
 * @param targetName 用户输入的目标地点，例如“成都东站”“杭州东站”“西湖”
 * @param cityName 目标地点所在城市，例如“成都市”“杭州市”
 * @param usage 使用场景，只能传 PICKUP 或 RETURN
 */
public record RentalStoreResolveRequest(
        @NotBlank String targetName, @NotBlank String cityName, @NotBlank String usage) {}

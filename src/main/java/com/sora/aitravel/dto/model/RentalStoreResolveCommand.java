package com.sora.aitravel.dto.model;

import com.sora.aitravel.common.enums.RentalStoreUsageEnum;

/**
 * 租车服务点解析命令。
 *
 * <p>这是后端内部模型，通常由工作流节点构造后传给 {@code RentalStoreService}。它不是前端 API 请求体。
 *
 * @param targetName 用户输入的目标地点，例如“成都东站”“杭州东站”“西湖”
 * @param cityName 目标地点所在城市，例如“成都市”“杭州市”
 * @param usage 取车或还车用途
 */
public record RentalStoreResolveCommand(
        String targetName, String cityName, RentalStoreUsageEnum usage) {}

package com.sora.aitravel.dto.model;

import java.util.List;

/**
 * 旅行交通方式建议。
 *
 * <p>用于生成行程前的中间判断结果，帮助模型决定路线是否偏自驾、公共交通或混合出行。
 *
 * @param mode 推荐方式，例如 SELF_DRIVE、PUBLIC_TRANSIT、MIXED
 * @param recommended 是否强推荐该方式
 * @param reason 推荐原因
 * @param tips 交通相关提示
 */
public record TravelModeDTO(String mode, Boolean recommended, String reason, List<String> tips) {}

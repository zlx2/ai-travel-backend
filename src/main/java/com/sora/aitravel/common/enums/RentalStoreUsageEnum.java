package com.sora.aitravel.common.enums;

import com.sora.aitravel.common.exception.BusinessException;

/**
 * 租车服务点使用场景。
 *
 * <p>该枚举只表达“这次解析出来的地点用于取车还是还车”，不代表库存、价格、订单状态。
 */
public enum RentalStoreUsageEnum {
    /** 取车点。 */
    PICKUP,

    /** 还车点。 */
    RETURN;

    /**
     * 将外部入参转换为枚举值。
     *
     * @param value 外部传入的用途字符串，允许大小写混用
     * @return 标准用途枚举
     * @throws IllegalArgumentException 当入参不是 PICKUP 或 RETURN 时抛出
     */
    public static RentalStoreUsageEnum from(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "usage 不能为空，只能是 PICKUP 或 RETURN");
        }

        try {
            return RentalStoreUsageEnum.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "usage 只能是 PICKUP 或 RETURN：" + value);
        }
    }
}

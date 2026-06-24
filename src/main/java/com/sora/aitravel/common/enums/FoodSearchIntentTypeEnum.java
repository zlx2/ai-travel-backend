package com.sora.aitravel.common.enums;

/**
 * 美食查询意图类型枚举。
 *
 * <p>用于标识用户这次美食查询到底属于哪一种场景。放在 common/enums 下，是因为它不是入参 DTO， 也不是返回对象，而是业务判断时使用的枚举值。
 */
public enum FoodSearchIntentTypeEnum {
    /** 查询用户当前位置附近，例如“附近有什么好吃的”“我身边有啥饭店”。 */
    NEAR_CURRENT,

    /** 查询某个文字地点附近，例如“洪崖洞附近火锅”“解放碑周边小吃”。 */
    NEAR_ADDRESS,

    /** 查询某个城市里的美食关键词，例如“重庆火锅推荐”“成都串串推荐”。 */
    CITY_KEYWORD
}

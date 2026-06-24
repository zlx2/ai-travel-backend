package com.sora.aitravel.common.enums;

/**
 * AI 需求分析状态枚举。
 *
 * <p>AI 行程规划的第一步——需求分析，仅允许以下四种业务状态。 模型异常应通过错误码返回，不应增加 FAIL 状态。
 */
public enum AnalyzeStatusEnum {
    /** 已就绪，需求信息足够，可以进入生成阶段。 */
    READY,
    /** 需要用户补充更多信息才能继续。 */
    NEED_MORE_INFO,
    /** 用户多个需求之间存在冲突，需要确认。 */
    CONFLICT,
    /** 需要用户在候选目的地中做出选择。 */
    NEED_DESTINATION_CHOICE
}

package com.sora.aitravel.common.enums;

/** AI 需求分析仅允许这四种业务状态；模型异常应走错误码，不能增加 FAIL。 */
public enum AnalyzeStatusEnum {
    READY,
    NEED_MORE_INFO,
    CONFLICT,
    NEED_DESTINATION_CHOICE
}

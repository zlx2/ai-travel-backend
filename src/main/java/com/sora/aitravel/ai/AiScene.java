package com.sora.aitravel.ai;

/** AI 调用场景，用于统一日志、异常和后续调用记录。 */
public enum AiScene {
    TRIP_ANALYZE("AI 分析"),
    TRIP_GENERATE("AI 行程生成"),
    AI_CHAT("AI 聊天"),
    TOOL_DEMO("AI 工具演示");

    private final String description;

    AiScene(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}

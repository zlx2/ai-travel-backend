package com.sora.aitravel.common.result;

/**
 * 骨架阶段的统一占位响应。
 *
 * <p>端点尚未接入业务实现时返回 501，避免用伪造数据制造“调用成功”的假象。实现对应 Service 后应删除调用点。
 */
public final class ScaffoldResponses {
    private ScaffoldResponses() {}

    public static <T> R<T> notImplemented() {
        return R.fail(501, "接口骨架已生成，业务逻辑待实现");
    }
}
